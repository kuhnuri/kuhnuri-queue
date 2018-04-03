package services

import java.time.{Clock, Duration, LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.UUID

import akka.actor.ActorSystem
import filters.TokenAuthorizationFilter.AUTH_TOKEN_HEADER
import generated.Tables._
import generated.enums.Status
import javax.inject.{Inject, Singleton}
import models._
import models.request.{Create, JobResult}
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.{Update => _, _}
import play.api.db.Database
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger, http}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

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
  private val timeout = Duration.ofMillis(configuration.getMillis("queue.timeout"))

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

  private def selectJob(sql: DSLContext): SelectJoinStep[Record6[String, String, String, /*String, Status,*/ Integer, OffsetDateTime,/* OffsetDateTime, String, */OffsetDateTime]] = {
    sql
      .select(JOB.UUID, JOB.INPUT, JOB.OUTPUT,
//        JOB.TRANSTYPE, JOB.STATUS,
        JOB.PRIORITY,
        JOB.CREATED,
//        JOB.PROCESSING, JOB.WORKER,
        JOB.FINISHED)
      .from(JOB)
  }

  override def contents(): Seq[Job] = {
    db.withConnection { connection =>
//      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
//      val query = selectJob(sql)
//        .orderBy(JOB.CREATED.desc)
//      val res = query
//        .fetch(Mappers.JobMapper)
//        .asScala
//      res
      List()
    }
  }

  override def get(id: String): Option[Job] =
    db.withConnection { connection =>
//      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
//      val query = selectJob(sql)
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
      val taskQuery = job.transtype.map { task =>
        sql
          .insertInto(TASK,
            TASK.UUID, TASK.TRANSTYPE, TASK.STATUS, TASK.PRIORITY, TASK.JOB
            //, JOB.TRANSTYPE
          )
          .values(
            task.id, task.transtype, Status.valueOf(task.status.toString), 0, jobID
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

  override def update(update: Update): Option[Task] = {
    db.withConnection { connection =>
      logger.info("update")
//      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
//      val now = OffsetDateTime.now(clock)
//      val query = sql
//        .update(JOB)
//        .set(JOB.STATUS, Status.valueOf(update.status.get.toString))
//        .set(update.status.get match {
//          case StatusString.Queue => JOB.CREATED
//          case StatusString.Process => JOB.PROCESSING
//          case StatusString.Done => JOB.FINISHED
//          case StatusString.Error => JOB.FINISHED
//        }, now)
//        .where(JOB.UUID.eq(update.id))
//      val res = query
//        .returning(JOB.UUID, JOB.STATUS)
//        .execute()
      None
    }
  }

  override def request(transtypes: List[String], worker: Worker): Option[Task] =
    db.withConnection { connection =>
      logger.debug("Request work for transtypes " + transtypes.mkString(", "))
//      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
//      val now = OffsetDateTime.now(clock)
//      val query = sql
//        .update(JOB)
//        .set(JOB.STATUS, Status.process)
//        .set(JOB.PROCESSING, now)
//        .set(JOB.WORKER, worker.id)
//        .where(JOB.UUID.in(
//          sql.select(JOB.UUID)
//            .from(JOB)
//            .where(JOB.STATUS.eq(Status.queue)
//              .and(JOB.TRANSTYPE.in(transtypes.asJava)))
//            .orderBy(JOB.PRIORITY.asc, JOB.CREATED.asc())
//            .limit(1)))
//      val res = query
//        .returning(JOB.UUID, JOB.INPUT, JOB.OUTPUT, JOB.TRANSTYPE, JOB.STATUS, JOB.CREATED, JOB.PROCESSING, JOB.FINISHED)
//        .fetchOne()
//      logger.debug(s"Request got back ${query}")
//      res match {
//        case null => None
//        case res =>
//          val job = Job(
//            res.getUuid,
//            res.getInput,
//            res.getOutput,
//            res.getTranstype,
//            Map.empty,
//            StatusString.parse(res.getStatus),
//            res.getPriority.intValue(),
//            res.getCreated.toLocalDateTime,
//            Some(res.getProcessing.toLocalDateTime),
//            Some(worker.id),
//            None//Some(res.getFinished.toLocalDateTime)
//          )
//          Some(job)
//      }
      None
    }

  // FIXME this should return a Try or Option
  override def submit(res: JobResult): Job =
    db.withConnection { connection =>
      //      logger.info(s"Submit $res")
//      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
//      val now = OffsetDateTime.now(clock)
//      sql
//        .update(JOB)
//        .set(JOB.STATUS, Status.valueOf(res.job.status.toString))
//        .set(JOB.FINISHED, now)
//        .where(JOB.UUID.eq(res.job.id))
//        .execute()
//      logStore.add(res.job.id, res.log)
//      res.job
      null
    }
}

private object Mappers {
  type ReviewStatus = String
  type CommentError = String

//  object JobMapper extends RecordMapper[Record10[String, String, String, String, Status, Integer, OffsetDateTime, OffsetDateTime, String, OffsetDateTime], Job] {
//    @Override
//    def map(c: Record10[String, String, String, String, Status, Integer, OffsetDateTime, OffsetDateTime, String, OffsetDateTime]): Job = {
//      Job(
//        c.value1,
//        c.value2,
//        c.value3,
//        c.value4,
//        Map.empty,
//        StatusString.parse(c.value5),
//        c.value6,
//        c.value7.toLocalDateTime,
//        Option(c.value8).map(_.toLocalDateTime),
//        Option(c.value9),
//        Option(c.value10).map(_.toLocalDateTime)
//      )
//    }
//  }

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
