package services

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.{Files, Paths}
import java.time.{Clock, Duration, LocalDateTime, ZoneOffset}
import java.util.UUID

import akka.actor.ActorSystem
import filters.TokenAuthorizationFilter.AUTH_TOKEN_HEADER
import javax.inject.{Inject, Singleton}
import models.Job._
import models._
import models.request.{Create, JobResult}
import play.api.libs.json.{JsError, Json}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger, http}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class SimpleQueue @Inject()(ws: WSClient,
                            configuration: Configuration,
                            clock: Clock,
                            actorSystem: ActorSystem)(implicit executionContext: ExecutionContext) extends Queue with Dispatcher {

  private val logger = Logger(this.getClass)
  private val timeout = Duration.ofMillis(configuration.getMillis("queue.timeout"))
  private val stateFile = Paths.get(configuration.get[String]("queue.temp"), "queue.json")

  val data: mutable.Map[String, Job] = load()

  actorSystem.scheduler.schedule(initialDelay = 10.seconds, interval = 1.minutes)(checkQueue)

  /**
    * Return stale tasks back to queue.
    */
  private def checkQueue(): Unit = {
    logger.debug("Check stale jobs")
    data.values
      .filter(hasJobTimedOut)
      .foreach { job =>
        logger.info(s"Return ${job.id} back to queue")
        val res = job.copy(
          transtype = job.transtype.map { task =>
            task.copy(
              input = None,
              output = None,
              status = StatusString.Queue,
              processing = Option.empty,
              worker = Option.empty
            )

          }
        )
        data += res.id -> res
        persist()
      }
  }

  private def hasJobTimedOut(job: Job): Boolean = {
    return job.transtype.find(hasTaskTimedOut).isDefined
  }

  private def hasTaskTimedOut(task: Task): Boolean = {
    // Is being processed
    if (task.processing.isDefined && task.finished.isEmpty) {
      // Timeout has occurred
      val now = LocalDateTime.now(clock)
      if (task.processing.map(_.plus(timeout).isBefore(now)).get) {
        // Worker cannot be contacted
        !pingWorker(task)
      }
    }
    return false
  }

  private def pingWorker(task: Task): Boolean = {
    WorkerStore.workers.get(task.worker.get).map { worker =>
      val workerUri = worker.uri.resolve("api/v1/status")
      logger.debug(s"Check worker status: ${workerUri}")
      val req: Future[Boolean] = ws.url(workerUri.toString)
        .addHttpHeaders(AUTH_TOKEN_HEADER -> worker.token)
        .withRequestTimeout(10000.millis)
        .get()
        .map(_.status == http.Status.OK)
      val res: Boolean = Await.result(req, 10000.millis)
      return res
    }
    return false
  }

  override def contents(): Seq[Job] =
    data.values.seq.toList.sortBy(_.created.toEpochSecond(ZoneOffset.UTC))

  override def get(id: String): Option[Job] =
    data.get(id)

  override def log(id: String, offset: Int): Option[Seq[String]] = ???

  override def add(newJob: Create): Job = {
    val id = UUID.randomUUID().toString
    val job = Job(
      id,
      newJob.input,
      newJob.output,
      newJob.transtype.map(t =>
        Task(
          UUID.randomUUID().toString,
          id,
          None,
          None,
          t,
          newJob.params,
          StatusString.Queue,
          None,
          None,
          None)
      ),
      newJob.priority.getOrElse(0),
      LocalDateTime.now(clock),
      None,
      StatusString.Queue)

    data += job.id -> job
    persist()
    job
  }

  override def update(update: Update): Option[Task] = {
    data.values.foreach { job =>
      val task = job.transtype.find(_.id == update.id)
      if (task.isDefined) {
        val tasks = job.transtype.map { task =>
          if (task.id == update.id) {
            task.copy(status = update.status.getOrElse(task.status))
          } else {
            task
          }
        }
        val res = job.copy(
          transtype = tasks,
          status = getStatus(tasks)
        )
        data += res.id -> res
        persist()
        return task
      }
    }
    None
  }

  override def request(transtypes: List[String], worker: Worker): Option[Task] = {
    def hasQueueTask(job: Job, transtypes: List[String]): Boolean = {
      if (job.finished.isDefined) {
        return false
      }
      getFirstQueueTask(job)
        .filter(task => transtypes.contains(task.transtype))
        .isDefined
    }

    def getFirstQueueTask(job: Job): Option[Task] = {
      job.transtype
        .find(task => task.status == StatusString.Queue)
        .headOption
    }

    def zipWithPreviousOutput(job: Job): Seq[(Task, Option[String])] =
      job.transtype.zip(
        List(Some(job.input)) ++ job.transtype.map(_.output)
      )

    return data.values
      .filter(job => hasQueueTask(job, transtypes))
      .toList
      .sortWith(compare)
      .headOption
      .flatMap { job =>
        getFirstQueueTask(job) match {
          case Some(task) => {
            var resTask: Task = null
            val tasksWithPrevious: Seq[(Task, Option[String])] = zipWithPreviousOutput(job)
            val tasks = tasksWithPrevious.map { case (t, previousOutput) =>
              if (t.id == task.id) {
                resTask = task.copy(
                  input = previousOutput,
                  output = Some(job.output),
                  status = StatusString.Process,
                  processing = Some(LocalDateTime.now(clock)),
                  worker = Some(worker.id)
                )
                resTask
              } else {
                task
              }
            }
            val res = job.copy(
              transtype = tasks
            )
            data += res.id -> res
            persist()
            Some(resTask)
          }
          case None => None
        }
      }
  }

  private def compare(j: Job, k: Job): Boolean = {
    val p = j.priority.compareTo(k.priority)
    if (p != 0) {
      return p < 0
    }
    j.created.toEpochSecond(ZoneOffset.UTC).compareTo(k.created.toEpochSecond(ZoneOffset.UTC)) < 0
  }

  override def submit(result: JobResult): Task = {
    logger.info(s"Submit ${result.task.id}")
    data.get(result.task.job) match {
      case Some(job) => {
        val finished = LocalDateTime.now(clock)
        var task: Task = null
        val tasks = job.transtype.map { t =>
          if (t.id == result.task.id) {
            task = t.copy(
              output = result.task.output,
              status = result.task.status,
              finished = Some(finished)
            )
            task
          } else {
            t
          }
        }
        val jobFinished = if (tasks.last.finished.isDefined) Some(finished) else None
        val jobOutput = if (tasks.last.id == result.task.id) tasks.last.output.get else job.output
        val jobStatus: StatusString = getStatus(tasks)
        val res: Job = job.copy(
          transtype = tasks,
          finished = jobFinished,
          output = jobOutput,
          status = jobStatus
        )
        logger.info(s" save ${res}")
        data += res.id -> res
        persist()
        task //res
      }
      case None => throw new IllegalStateException("Unable to find matching Job")
    }
  }

  protected def persist(): Unit = {
    val out = Files.newBufferedWriter(stateFile, UTF_8, CREATE)
    try {
      out.write(Json.toJson(data).toString())
    } catch {
      case e: IOException => {
        logger.error("Failed to persist queue state", e)
      }
    } finally {
      out.close()
    }
  }

  protected def load(): mutable.Map[String, Job] = {
    if (Files.exists(stateFile)) {
      logger.info(s"Read ${stateFile}")
      val in = Files.newInputStream(stateFile)
      try {
        Json.parse(in).validate[Map[String, Job]].map {
          case req: Map[String, Job] => {
            mutable.Map(req.toSeq: _*)
          }
        }.recoverTotal {
          e => {
            logger.error("Failed to read queue state:" + JsError.toJson(e))
            mutable.Map[String, Job]()
          }
        }
      } catch {
        case e: IOException => {
          logger.error("Failed to read queue state file", e)
          mutable.Map[String, Job]()
        }
      } finally {
        in.close()
      }
    }
  }

  private def getStatus(tasks: Seq[Task]): StatusString =
    if (tasks.forall(_.status == StatusString.Queue)) StatusString.Queue
    else if (tasks.forall(_.status == StatusString.Done)) StatusString.Done
    else if (tasks.exists(_.status == StatusString.Error)) StatusString.Error
    else StatusString.Process
}




