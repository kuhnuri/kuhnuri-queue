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
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val nowMinusTimeout = getOffsetDateTime(LocalDateTime.now(clock).minus(timeout))
      val nullTimestamp: OffsetDateTime = null
      selectJob(sql)
        .where(QUEUE.FINISHED.isNull
          .and(QUEUE.PROCESSING.isNotNull)
          .and(QUEUE.PROCESSING.lt(nowMinusTimeout))
        )
        .fetch(Mappers.JobMapper)
        .asScala
        .filter { job => !pingWorker(job) }
        .foreach { job =>
          logger.info(s"Return ${job.id} back to queue")
          try {
            sql
              .update(QUEUE)
              .set(QUEUE.STATUS, Status.queue)
              .set(QUEUE.PROCESSING, nullTimestamp)
              .where(QUEUE.UUID.eq(job.id))
              .execute()
          } catch {
            case e: DataAccessException =>
              logger.error(s"Failed to return stale job to queue: ${e.getMessage}", e)
          }
        }
    }
  }

  private def getOffsetDateTime(dateTime: LocalDateTime): OffsetDateTime = {
    val offset = clock.getZone.getRules.getOffset(dateTime)
    OffsetDateTime.of(dateTime, offset)
  }

  private def pingWorker(job: Job): Boolean = {
    // FIXME: Store worker ID to Job so we can support multiple workers
    WorkerStore.workers.values.headOption.map { worker =>
      val workerUri = worker.uri.resolve("api/v1/status")
      //          logger.debug(s"Check worker status: ${workerUri}")
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

  private def selectJob(sql: DSLContext): SelectJoinStep[Record9[String, String, String, String, Status, Integer, OffsetDateTime, OffsetDateTime, OffsetDateTime]] = {
    sql
      .select(QUEUE.UUID, QUEUE.INPUT, QUEUE.OUTPUT, QUEUE.TRANSTYPE, QUEUE.STATUS, QUEUE.PRIORITY,
        QUEUE.CREATED, QUEUE.PROCESSING, QUEUE.FINISHED)
      .from(QUEUE)
  }

  override def contents(): Seq[Job] = {
    db.withConnection { connection =>
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val query = selectJob(sql)
        .orderBy(QUEUE.CREATED.desc)
      val res = query
        .fetch(Mappers.JobMapper)
        .asScala
      res
    }
  }

  override def get(id: String): Option[Job] =
    db.withConnection { connection =>
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val query = selectJob(sql)
        .where(QUEUE.UUID.eq(id))
      val res = query
        .fetchOne(Mappers.JobMapper)
      Option(res)
    }

  override def log(id: String, offset: Int): Option[Seq[String]] =
    logStore.get(id)

  override def add(job: Create): Job = {
    db.withConnection { connection =>
      val created = LocalDateTime.now(ZoneOffset.UTC)
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val query = sql
        .insertInto(QUEUE,
          QUEUE.UUID, QUEUE.CREATED, QUEUE.INPUT, QUEUE.OUTPUT, QUEUE.TRANSTYPE)
        .values(UUID.randomUUID().toString, OffsetDateTime.of(created, ZoneOffset.UTC),
          job.input, job.output, job.transtype)
      val res = query
        .returning(QUEUE.UUID, QUEUE.STATUS)
        .fetchOne()
      Job(res.getUuid, job.input, job.output, job.transtype, Map.empty, StatusString.parse(res.getStatus),
        job.priority.getOrElse(0), created, None, None)
    }
  }

  override def update(update: Update): Option[Job] = {
    db.withConnection { connection =>
      logger.info("update")
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val now = OffsetDateTime.now()
      val query = sql
        .update(QUEUE)
        .set(QUEUE.STATUS, Status.valueOf(update.status.get.toString))
        .set(update.status.get match {
          case StatusString.Queue => QUEUE.CREATED
          case StatusString.Process => QUEUE.PROCESSING
          case StatusString.Done => QUEUE.FINISHED
          case StatusString.Error => QUEUE.FINISHED
        }, now)
        .where(QUEUE.UUID.eq(update.id))
      val res = query
        .returning(QUEUE.UUID, QUEUE.STATUS)
        .execute()
      None
    }
  }

  override def request(transtypes: List[String]): Option[Job] =
    db.withConnection { connection =>
      logger.debug("Request work for transtypes " + transtypes.mkString(", "))
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val now = OffsetDateTime.now()
      val query = sql
        .update(QUEUE)
        .set(QUEUE.STATUS, Status.process)
        .set(QUEUE.PROCESSING, now)
        .where(QUEUE.UUID.in(
          sql.select(QUEUE.UUID)
            .from(QUEUE)
            .where(QUEUE.STATUS.eq(Status.queue)
              .and(QUEUE.TRANSTYPE.in(transtypes.asJava)))
            .orderBy(QUEUE.PRIORITY.asc, QUEUE.CREATED.asc())
            .limit(1)))
      val res = query
        .returning(QUEUE.UUID, QUEUE.INPUT, QUEUE.OUTPUT, QUEUE.TRANSTYPE, QUEUE.STATUS, QUEUE.CREATED, QUEUE.PROCESSING, QUEUE.FINISHED)
        .fetchOne()
      logger.debug(s"Request got back ${query}")
      res match {
        case null => None
        case res => Some(Job(res.getUuid, res.getInput, res.getOutput,
          res.getTranstype, Map.empty, StatusString.parse(res.getStatus), res.getPriority.intValue(),
          res.getCreated.toLocalDateTime, Some(res.getProcessing.toLocalDateTime), Some(res.getFinished.toLocalDateTime)))
      }
    }

  // FIXME this should return a Try or Option
  override def submit(res: JobResult): Job =
    db.withConnection { connection =>
      //      logger.info(s"Submit $res")
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      val now = OffsetDateTime.now()
      sql
        .update(QUEUE)
        .set(QUEUE.STATUS, Status.valueOf(res.job.status.toString))
        .set(QUEUE.FINISHED, now)
        .where(QUEUE.UUID.eq(res.job.id))
        .execute()
      logStore.add(res.job.id, res.log)
      res.job
    }
}

private object Mappers {
  type ReviewStatus = String
  type CommentError = String

  object JobMapper extends RecordMapper[Record9[String, String, String, String, Status, Integer, OffsetDateTime, OffsetDateTime, OffsetDateTime], Job] {
    @Override
    def map(c: Record9[String, String, String, String, Status, Integer, OffsetDateTime, OffsetDateTime, OffsetDateTime]): Job = {
      Job(
        c.value1,
        c.value2,
        c.value3,
        c.value4,
        Map.empty,
        StatusString.parse(c.value5),
        c.value6,
        c.value7.toLocalDateTime,
        Option(c.value8).map(_.toLocalDateTime),
        Option(c.value9).map(_.toLocalDateTime)
      )
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
