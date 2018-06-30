package services

import java.time._

import akka.actor.ActorSystem
import generated.Tables._
import generated.enums.Status
import javax.inject.{Inject, Singleton}
import models._
import models.request.{Create, Filter, JobResult}
import org.jooq.impl.DSL
import org.jooq.{Update => _, _}
import play.api.db.Database
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * A database backed queue
  *
  * @param db       database for queue state
  * @param logStore conversion log store
  */
@Singleton
class DBQueue @Inject()(db: Database,
                        logStore: LogStore,
                        ws: WSClient,
                        configuration: Configuration,
                        clock: Clock,
                        actorSystem: ActorSystem)(implicit executionContext: ExecutionContext) extends Queue with Dispatcher {

  private val logger = Logger(this.getClass)
  private val timeout = java.time.Duration.ofMillis(configuration.getMillis("queue.timeout"))

  actorSystem.scheduler.schedule(initialDelay = 10.seconds, interval = 1.minutes)(checkQueue)

  private def checkQueue(): Unit = {
    logger.debug("Check stale jobs")
    db.withConnection { connection =>
      //      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      //      val nowMinusTimeout = getOffsetDateTime(LocalDateTime.now(clock).minus(timeout))
      //      val nullTimestamp: OffsetDateTime = null
      //      val nullString: String = null
      //      selectJob(sql)
      //        .where(JOB.FINISHED.isNull
      //          .and(JOB.PROCESSING.isNotNull)
      //          .and(JOB.PROCESSING.lt(nowMinusTimeout))
      //        )
      //        .fetch(Mappers.JobMapper)
      //        .asScala
      //        .filter { job => !pingWorker(job) }
      //        .foreach { job =>
      //          logger.info(s"Return ${job.id} back to queue")
      //          try {
      //            sql
      //              .update(JOB)
      //              .set(JOB.STATUS, Status.queue)
      //              .set(JOB.PROCESSING, nullTimestamp)
      //              .set(JOB.WORKER, nullString)
      //              .where(JOB.UUID.eq(job.id))
      //              .execute()
      //          } catch {
      //            case e: DataAccessException =>
      //              logger.error(s"Failed to return stale job to queue: ${e.getMessage}", e)
      //          }
      //        }
    }
  }

  private def getOffsetDateTime(dateTime: LocalDateTime): OffsetDateTime = {
    val offset = clock.getZone.getRules.getOffset(dateTime)
    OffsetDateTime.of(dateTime, offset)
  }

  private def pingWorker(job: Job): Boolean = {
    //    WorkerStore.workers.get(job.worker.get).map { worker =>
    //      val workerUri = worker.uri.resolve("api/v1/status")
    ////      logger.debug(s"Check worker status: ${workerUri}")
    //      val req: Future[Boolean] = ws.url(workerUri.toString)
    //        .addHttpHeaders(AUTH_TOKEN_HEADER -> worker.token)
    //        .withRequestTimeout(10000.millis)
    //        .get()
    //        .map(_.status == http.Status.OK)
    //      val res: Boolean = Await.result(req, 10000.millis)
    //      return res
    //    }
    return false
  }

  private def selectJob(sql: DSLContext): SelectJoinStep[Record6[String, String, String, /*String, Status,*/ Integer, OffsetDateTime, /* OffsetDateTime, String, */ OffsetDateTime]] = {
    sql
      .select(JOB.UUID, JOB.INPUT, JOB.OUTPUT,
        //        JOB.TRANSTYPE, JOB.STATUS,
        JOB.PRIORITY,
        JOB.CREATED,
        //        JOB.PROCESSING, JOB.WORKER,
        JOB.FINISHED)
      .from(JOB)
  }

  override def contents(filter: Filter): Seq[Job] = {
    db.withConnection { connection =>
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      //[string, string, string,int, date, date, status]
      val query =
        """
        SELECT
          job.uuid,
          job.input,
          job.output,
          job.priority,
          job.created,
          job.finished,
          --   task.uuid,
          --   task.transtype,
          --   task.input,
          --   task.output,
          CASE
          WHEN task.error
            THEN 'error'
          WHEN task.queue
            THEN 'queue'
          WHEN task.done
            THEN 'done'
          ELSE 'process'
          END AS STATUS
        --   task.id,
        --   task.processing,
        --   task.finished,
        --   task.worker,
        --   task.job,
        --   task.position
        FROM job
          INNER JOIN (
                       SELECT
                         job,
                         bool_or('error' IN (status))  AS error,
                         bool_and('queue' IN (status)) AS queue,
                         bool_and('done' IN (status))  AS done
                       FROM task
                       GROUP BY job
                     ) AS task
            ON job.id = task.job;""";
      val res = sql
        .fetch(query)
        .asInstanceOf[Result[Record7[String, String, String, Integer, OffsetDateTime, OffsetDateTime, String]]]
        .map(Mappers.JobMapper)
        .asScala
      res
    }
  }

  override def get(id: String): Option[Job] =
    db.withConnection { connection =>
      //      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      //      val query = sql
      //        .select(JOB.UUID, JOB.INPUT, JOB.OUTPUT,
      //          //        JOB.TRANSTYPE, JOB.STATUS,
      //          JOB.PRIORITY,
      //          JOB.CREATED,
      //          //        JOB.PROCESSING, JOB.WORKER,
      //          JOB.FINISHED)
      //        .from(JOB)
      //        .leftJoin(TASK).on(TASK.JOB.eq(JOB.ID))
      //        .where(JOB.UUID.eq(id))
      //      val res = query
      //        .fetchOne(Mappers.JobMapper)
      //      Option(res)
      None
    }

  override def log(id: String, offset: Int): Option[Seq[String]] =
    logStore.get(id)

  override def add(create: Create): Job = {
    val job = create.toJob
    db.withConnection { connection =>
      val created = LocalDateTime.now(clock)
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      // job
      val query = sql
        .insertInto(JOB,
          JOB.UUID, JOB.CREATED, JOB.INPUT, JOB.OUTPUT, JOB.PRIORITY
          //, JOB.TRANSTYPE
        )
        .values(job.id, OffsetDateTime.of(created, ZoneOffset.UTC),
          job.input, job.output, job.priority
          //, job.transtype
        )
      val res = query
        .returning(JOB.UUID, JOB.ID
          //, JOB.STATUS
        )
        .fetchOne()
      val jobID = res.getId
      // tasks
      val taskQuery = job.transtype.zipWithIndex.map {
        case (task, i) =>
          sql
            .insertInto(TASK,
              TASK.UUID, TASK.TRANSTYPE, TASK.STATUS, TASK.JOB, TASK.POSITION
              //, JOB.TRANSTYPE
            )
            .values(
              task.id, task.transtype, Status.valueOf(task.status.toString), jobID, i + 1
            )
      }.asJavaCollection
      val taskRes = sql
        .batch(taskQuery)
        .execute()

      job.copy(
        id = res.getUuid
      )
    }
  }

//  override def update(update: Update): Option[Task] = {
//    db.withConnection { connection =>
//      //      logger.info("update")
//      //      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
//      //      val now = OffsetDateTime.now(clock)
//      //      val query = sql
//      //        .update(TASK)
//      //        .set(TASK.STATUS, Status.valueOf(update.status.get.toString))
//      //        .set(update.status.get match {
//      //          case StatusString.Queue => TASK.CREATED
//      //          case StatusString.Process => TASK.PROCESSING
//      //          case StatusString.Done => TASK.FINISHED
//      //          case StatusString.Error => TASK.FINISHED
//      //        }, now)
//      //        .where(TASK.UUID.eq(update.id))
//      //      val res = query
//      //        .returning(TASK.UUID, TASK.STATUS)
//      //        .execute()
//      None
//    }
//  }

  override def request(transtypes: List[String], worker: Worker): Option[Task] =
    db.withConnection { connection =>
      logger.debug("Request work for transtypes " + transtypes.mkString(", "))
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val now = OffsetDateTime.now(clock)

      val prev = TASK.as("prev")
      val first = TASK.as("first")
      val query: Select[_ <: Record1[Integer]] = sql.select(first.ID)
        .from(first)
        .leftJoin(JOB).on(JOB.ID.eq(first.JOB))
        .where(first.STATUS.eq(Status.queue)
          .and(first.TRANSTYPE.in(transtypes.asJava))
          .and(first.POSITION.eq(1)
            .or(
              first.JOB.in(
                //              sql.fetchExists(
                // exists(
                sql.select(prev.JOB)
                  .from(prev)
                  .where(prev.JOB.eq(first.JOB)
                    .and(prev.STATUS.eq(Status.done))
                    .and(prev.POSITION.eq(first.POSITION.sub(1)))
                  )
              )
            )
          )
        )
        .orderBy(JOB.PRIORITY.desc(), JOB.CREATED.asc(), first.POSITION.asc())
        .limit(1)
      val update = sql
        .update(TASK)
        .set(TASK.STATUS, Status.process)
        .set(TASK.PROCESSING, now)
        .set(TASK.WORKER, worker.id)
        .where(TASK.ID.in(query))
      val res = update
        .returning(TASK.UUID, TASK.ID, TASK.JOB, TASK.POSITION, TASK.STATUS, TASK.PROCESSING)
        .fetchOne()

      val (input: String, output: String, jobId: String) = res.getPosition.intValue() match {
        case 1 => {
          val job = sql
            .select(JOB.OUTPUT, JOB.INPUT, JOB.UUID)
            .from(JOB)
            .where(JOB.ID.eq(res.getJob))
            .fetchOne()
          (job.get(JOB.INPUT), job.get(JOB.OUTPUT), job.get(JOB.UUID))
        }
        case _ => {
          val prevTask = sql
            .select(TASK.OUTPUT, JOB.OUTPUT, JOB.UUID)
            .from(TASK)
            .join(JOB).on(TASK.JOB.eq(JOB.ID))
            .where(TASK.JOB.eq(res.getJob)
              .and(TASK.POSITION.eq(res.getPosition - 1)))
            .fetchOne()
          (prevTask.get(TASK.OUTPUT), prevTask.get(JOB.OUTPUT), prevTask.get(JOB.UUID))
        }
      }

      val resourceUpdate = sql
        .update(TASK)
        .set(TASK.INPUT, input)
        .set(TASK.OUTPUT, output)
        .execute()

      logger.debug(s"Request got back ${update}")
      resourceUpdate match {
        case 0 => None
        case _ =>
          val task = Task(
            res.getUuid,
            jobId,
            Some(input),
            Some(output),
            res.getTranstype,
            Map.empty,
            StatusString.parse(res.getStatus),
            //            res.getPriority.intValue(),
            //            res.getCreated.toLocalDateTime,
            Some(res.getProcessing.toLocalDateTime),
            Some(worker.id),
            None //Some(res.getFinished.toLocalDateTime)
          )
          Some(task)
      }
    }

  // FIXME this should return a Try or Option
  override def submit(res: JobResult): Task =
    db.withConnection { connection =>
      logger.info(s"Submit $res")
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val now = OffsetDateTime.now(clock)
      sql
        .update(TASK)
        .set(TASK.STATUS, Status.valueOf(res.task.status.toString))
        .set(TASK.FINISHED, now)
        .where(TASK.UUID.eq(res.task.id))
        .execute()
      logStore.add(res.task.id, res.log)
      res.task
    }
}

private object Mappers {
  type ReviewStatus = String
  type CommentError = String

  object JobMapper extends RecordMapper[Record7[String, String, String, /*String, Status, */ Integer, OffsetDateTime, /* OffsetDateTime, String,*/ OffsetDateTime, String], Job] {
    @Override
    def map(c: Record7[String, String, String, /*String, Status, */ Integer, OffsetDateTime, /*OffsetDateTime, String,*/ OffsetDateTime, String]): Job = {
      Job(
        c.value1,
        c.value2,
        c.value3,
        //          c.value4,
        List.empty,
        //          Map.empty,
        //          StatusString.parse(c.value5),
        c.value4.intValue(),
        //          c.value7.toLocalDateTime,
        c.value5.toLocalDateTime,
        //          Option(c.value9),
        Option(c.value6).map(_.toLocalDateTime),
        StatusString(c.value7)
      )

      //        id: String,
      //        input: String,
      //        output: String,
      //        transtype: Seq[Task],
      //        priority: Int,
      //        created: LocalDateTime,
      //        finished: Option[LocalDateTime]

    }
  }

  //  object JobStatusMapper extends RecordMapper[Record3[String, String, Status], JobStatus] {
  //    @Override
  //    def map(c: Record3[String, String, Status]): JobStatus = {
  //      JobStatus(
  //        c.value1,
  //        Option(c.value2),
  //        StatusString.parse(c.value3.toString)
  //      )
  //    }
  //  }

}
