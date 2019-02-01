package services

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.time.{Clock, Duration, LocalDateTime, ZoneOffset}
import java.util.UUID

import akka.actor.ActorSystem
import filters.TokenAuthorizationFilter.AUTH_TOKEN_HEADER
import javax.inject.{Inject, Singleton}
import models.Job._
import models._
import models.request.{Create, Filter, JobResult}
import play.api.libs.json.{JsError, Json}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger, http}

import scala.collection.JavaConverters._
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
  private val archiveDir = Paths.get(configuration.get[String]("queue.data"), "archive")
  private val stateFile = Paths.get(configuration.get[String]("queue.data"), "queue.json")

  val data: mutable.Map[String, Job] = load()

  actorSystem.scheduler.schedule(initialDelay = 10.seconds, interval = 15.minutes)(checkQueue)

  /**
    * Return stale tasks back to queue.
    */
  private def checkQueue(): Unit = {
    def hasJobTimedOut(job: Job): Boolean = {
      return job.transtype.find(hasTaskTimedOut).isDefined
    }

    def hasTaskTimedOut(task: Task): Boolean = {
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

    def pingWorker(task: Task): Boolean = {
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
        data.synchronized {
          data += res.id -> res
        }
        persist()
      }
  }

  override def contents(filter: Filter): Seq[Job] = {
    val values = filter match {
      case Filter(None) => data.valuesIterator
      case Filter(status) => data.valuesIterator.filter(job => status.map(s => s == job.status).getOrElse(true))
    }
    values.toList.sortBy(_.created.toEpochSecond(ZoneOffset.UTC))
  }

  override def get(id: String): Option[Job] =
    data.get(id).orElse(loadArchive(id))

  override def log(id: String, offset: Int): Option[Seq[String]] = ???

  override def add(newJob: Create): Job = {
    val id = newJob.id.getOrElse(UUID.randomUUID().toString)
    val transtypes = getTranstypes(newJob.transtype)
    val params = getParams(newJob.transtype) ++ newJob.params
    val job = Job(
      id,
      newJob.input,
      newJob.output,
      transtypes.map(t =>
        Task(
          UUID.randomUUID().toString,
          id,
          None,
          None,
          t,
          params,
          StatusString.Queue,
          None,
          None,
          None)
      ),
      newJob.priority.getOrElse(0),
      LocalDateTime.now(clock),
      None,
      StatusString.Queue)

    data.synchronized {
      data += job.id -> job
    }
    persist()
    job
  }

  private val transtypeAlias: Map[String, Seq[String]] = configuration.underlying
    .getConfigList("queue.alias")
    .asScala
    .map(alias => alias.getString("name") -> alias.getStringList("transtypes").asScala)
    .toMap

  def getTranstypes(transtypes: Seq[String]): Seq[String] =
    transtypes.flatMap(transtype => transtypeAlias.getOrElse(transtype, List(transtype)))

  private val transtypeParams: Map[String, Map[String, String]] = configuration.underlying
    .getConfigList("queue.alias")
    .asScala
    .map(alias =>
      alias.getString("name") -> alias.getConfig("params")
        .entrySet.asScala
        .map(entry => entry.getKey -> entry.getValue.unwrapped.toString)
        .toMap
    )
    .toMap

  def getParams(transtypes: Seq[String]): Map[String, String] =
    transtypes
      .flatMap(transtypeParams.getOrElse(_, Map.empty).toList)
      .groupBy(_._1)
      .map(_._2.head)

  //  override def update(update: Update): Option[Task] = {
  //    data.values.foreach { job =>
  //      val task = job.transtype.find(_.id == update.id)
  //      if (task.isDefined) {
  //        val tasks = job.transtype.map { task =>
  //          if (task.id == update.id) {
  //            task.copy(status = update.status.getOrElse(task.status))
  //          } else {
  //            task
  //          }
  //        }
  //        val res = job.copy(
  //          transtype = tasks,
  //          status = getStatus(tasks)
  //        )
  //        data += res.id -> res
  //        persist()
  //        return task
  //      }
  //    }
  //    None
  //  }

  override def request(transtypes: List[String], worker: Worker): Option[Task] = {
    def hasQueueTask(job: Job, transtypes: List[String]): Boolean = {
      if (job.finished.isDefined) {
        return false
      }
      if (job.transtype.exists(task => task.status == StatusString.Process || task.status == StatusString.Error)) {
        return false
      }
      getFirstQueueTask(job)
        .filter(task => transtypes.contains(task.transtype))
        .isDefined
    }

    def getFirstQueueTask(job: Job): Option[Task] = {
      job.transtype
        .span(_.status == StatusString.Done)
        ._2
        .headOption
        .filter(_.status == StatusString.Queue)
    }

    def zipWithPreviousOutput(job: Job): Seq[(Task, Option[String])] =
      job.transtype.zip(
        List(Some(job.input)) ++ job.transtype.map(_.output)
      )

    /** Job compare by created field. */
    def compare(j: Job, k: Job): Boolean = {
      val p = j.priority.compareTo(k.priority)
      if (p != 0) {
        return p < 0
      }
      j.created.toEpochSecond(ZoneOffset.UTC).compareTo(k.created.toEpochSecond(ZoneOffset.UTC)) < 0
    }

    return data.values
      .filter(job => hasQueueTask(job, transtypes))
      .toList
      .sortWith(compare)
      .headOption
      .flatMap { job =>
        data.synchronized {
          getFirstQueueTask(job) match {
            case Some(task) => {
              assert(task.status == StatusString.Queue)
              var resTask: Task = null
              val tasksWithPrevious: Seq[(Task, Option[String])] = zipWithPreviousOutput(job)
              val lastTaskId = job.transtype.last.id
              val tasks = tasksWithPrevious.map { case (t, previousOutput) =>
                if (t.id == task.id) {
                  resTask = t.copy(
                    input = previousOutput,
                    output = if (t.id == lastTaskId) Some(job.output) else None,
                    status = StatusString.Process,
                    processing = Some(LocalDateTime.now(clock)),
                    worker = Some(worker.id)
                  )
                  resTask
                } else {
                  t
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
  }

  override def submit(result: JobResult): Task = {
    logger.info(s"Submit ${result.task.id}")
    logger.debug(result.toString)
    data.get(result.task.job) match {
      case Some(job) => {
        val finished = LocalDateTime.now(clock)
        var task: Task = null
        val tasks = job.transtype.map { t =>
          if (t.id == result.task.id) {
            task = t.copy(
              output = result.task.output,
              status = result.task.status,
              params = t.params ++ result.task.params,
              finished = Some(finished)
            )
            task
          } else {
            t
          }
        }
        val jobFinished = if (tasks.last.finished.isDefined) Some(finished) else None
        val jobOutput = if (tasks.last.id == result.task.id && result.task.output.isDefined) result.task.output.get else job.output
        val jobStatus: StatusString = getStatus(tasks)
        val res: Job = job.copy(
          transtype = tasks,
          finished = jobFinished,
          output = jobOutput,
          status = jobStatus
        )
        logger.info(s" save ${res}")
        if (res.status == StatusString.Done || res.status == StatusString.Error) {
          archive(res)
          data.synchronized {
            data -= res.id
            //            data += res.id -> res
          }
          persist()
        } else {
          data.synchronized {
            data += res.id -> res
          }
          persist()
        }
        task //res
      }
      case None => throw new IllegalStateException("Unable to find matching Job")
    }
  }

  /**
    * Persist queue to disk.
    */
  protected def persist(): Unit = {
    val out = Files.newBufferedWriter(stateFile, UTF_8)
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

  /**
    * Load persisted queue from disk.
    */
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
    } else {
      mutable.Map.empty
    }
  }

  /**
    * Archive job to disk.
    */
  protected def archive(res: Job): Unit = {
    val file = archiveDir.resolve(s"${res.id}.json")
    if (!Files.exists(file.getParent)) {
      Files.createDirectories(file.getParent)
    }
    val out = Files.newBufferedWriter(file, UTF_8)
    try {
      out.write(Json.toJson(res).toString())
    } catch {
      case e: IOException => {
        logger.error("Failed to archive job", e)
      }
    } finally {
      out.close()
    }
  }

  /**
    * Load persisted queue from disk.
    */
  protected def loadArchive(id: String): Option[Job] = {
    val file = archiveDir.resolve(s"${id}.json")
    if (Files.exists(file)) {
      logger.info(s"Read ${file}")
      val in = Files.newInputStream(file)
      try {
        Json.parse(in).validate[Job].map {
          case job: Job => Option(job)
        }.recoverTotal {
          e => {
            logger.error("Failed to read job archive:" + JsError.toJson(e))
            Option.empty
          }
        }
      } catch {
        case e: IOException => {
          logger.error("Failed to read job archive", e)
          Option.empty
        }
      } finally {
        in.close()
      }
    } else {
      Option.empty
    }
  }

  /**
    * Get job status from task statuses.
    */
  def getStatus(tasks: Seq[Task]): StatusString =
    if (tasks.forall(_.status == StatusString.Queue)) StatusString.Queue
    else if (tasks.forall(_.status == StatusString.Done)) StatusString.Done
    else if (tasks.exists(_.status == StatusString.Error)) StatusString.Error
    else StatusString.Process
}




