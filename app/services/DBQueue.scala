package services

import java.sql.Timestamp
import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}

import generated.Tables._
import generated.enums.Status
import models._
import org.jooq.impl.DSL
import org.jooq.{Update => _, _}
import play.api.Logger
import play.api.db.Database

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

/**
  * A database backed queue
  *
  * @param db database for queue state
  * @param logStore conversion log store
  */
@Singleton
class DBQueue @Inject()(db: Database, logStore: LogStore) extends Queue with Dispatcher {

  private val logger = Logger(this.getClass)

  override def contents(): List[Job] =
    db.withConnection { connection =>
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      sql
        .select(QUEUE.UUID, QUEUE.INPUT, QUEUE.OUTPUT, QUEUE.TRANSTYPE, QUEUE.STATUS)
        .from(QUEUE)
        .orderBy(QUEUE.CREATED.desc)
        .fetch(Mappers.JobMapper).asScala.toList
    }

  override def get(id: String): Option[Job] =
    db.withConnection { connection =>
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      var query = sql
        .select(QUEUE.UUID, QUEUE.INPUT, QUEUE.OUTPUT, QUEUE.TRANSTYPE, QUEUE.STATUS)
        .from(QUEUE)
        .where(QUEUE.UUID.eq(id))
      Option(query
        .fetchOne(Mappers.JobMapper))
    }

  override def log(id: String, offset: Int): Option[Seq[String]] =
    logStore.get(id)

  override def add(job: Create): Job = {
    db.withConnection { connection =>
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      var query = sql
        .insertInto(QUEUE,
          QUEUE.UUID, QUEUE.CREATED, QUEUE.INPUT, QUEUE.OUTPUT, QUEUE.TRANSTYPE)
        .values(UUID.randomUUID().toString, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)),
          job.input, job.output, job.transtype)
        .returning(QUEUE.UUID, QUEUE.STATUS)
        .fetchOne()
      Job(query.getUuid, job.input, job.output, job.transtype, Map.empty, StatusString.parse(query.getStatus))
    }
  }

  override def update(update: Update): Option[Job] = {
    db.withConnection { connection =>
      logger.info("update")
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      var query = sql
        .update(QUEUE)
        .set(QUEUE.STATUS, Status.valueOf(update.status.get.toString))
        .where(QUEUE.UUID.eq(update.id))
        .returning(QUEUE.UUID, QUEUE.STATUS)
        .execute()
      None
    }
  }

  override def request(transtypes: List[String]): Option[Job] =
    db.withConnection { connection =>
      logger.debug("Request work for transtypes " + transtypes.mkString(", "))
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      var query = sql
        .update(QUEUE)
        .set(QUEUE.STATUS, Status.process)
        .where(QUEUE.UUID.in(
          sql.select(QUEUE.UUID)
            .from(QUEUE)
            .where(QUEUE.STATUS.eq(Status.queue)
              .and(QUEUE.TRANSTYPE.in(transtypes)))
            .orderBy(QUEUE.CREATED.asc())
            .limit(1)))
        .returning(QUEUE.UUID, QUEUE.INPUT, QUEUE.OUTPUT, QUEUE.TRANSTYPE, QUEUE.STATUS)
        .fetchOne()
      logger.debug(s"Request got back ${query}")
      query match {
        case null => None
        case res => Some(Job(res.getUuid, res.getInput, res.getOutput,
          res.getTranstype, Map.empty, StatusString.parse(res.getStatus)))
      }
    }

  // FIXME this should return a Try or Option
  override def submit(res: JobResult): Job =
    db.withConnection { connection =>
      //      logger.info(s"Submit $res")
      val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
      sql
        .update(QUEUE)
        .set(QUEUE.STATUS, Status.done)
        .where(QUEUE.UUID.eq(res.job.id))
        .execute()
      logStore.add(res.job.id, res.log)
      res.job
    }
}

private object Mappers {
  type ReviewStatus = String
  type CommentError = String

  object JobMapper extends RecordMapper[Record5[String, String, String, String, Status], Job] {
    @Override
    def map(c: Record5[String, String, String, String, Status]): Job = {
      Job(
        c.value1,
        c.value2,
        c.value3,
        c.value4,
        Map.empty,
        StatusString.parse(c.value5)
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
