package services

import java.time.{Clock, Duration, LocalDateTime, ZoneOffset}

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import models._
import play.api.{Configuration, Logger}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class SimpleQueue @Inject()(configuration: Configuration,
                            clock: Clock,
                            actorSystem: ActorSystem)(implicit executionContext: ExecutionContext) extends Queue with Dispatcher {

  private val logger = Logger(this.getClass)
  private val timeout = Duration.ofMillis(configuration.getMillis("queue.timeout"))

  val data: mutable.Map[String, Job] = mutable.Map(
//    "id-A" -> Job("id-A",
//      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
//      "file:/Volumes/tmp/out/",
//      "html5",
//      Map.empty,
//      StatusString.Queue,
//      0,
//      LocalDateTime.now.minusHours(1), None, None),
//    "id-A1" -> Job("id-A1",
//      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
//      "file:/Volumes/tmp/out/",
//      "html5",
//      Map.empty,
//      StatusString.Queue,
//      0,
//      LocalDateTime.now.minusHours(2), None, None),
//    "id-B" -> Job("id-B",
//      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
//      "file:/Volumes/tmp/out/",
//      "pdf",
//      Map.empty,
//      StatusString.Queue,
//      0,
//      LocalDateTime.now, None, None)
  )

  actorSystem.scheduler.schedule(initialDelay = 0.microseconds, interval = 1.minutes) {
    data.values
      .filter { job =>
        job.finished.isEmpty &&
          job.processing
            .map { dateTime =>
              dateTime.plus(timeout).isBefore(LocalDateTime.now(clock))
            }
            .getOrElse(false)
      }
      .foreach { job =>
        logger.info(s"Return ${job.id} back to queue")
        val res = job.copy(status = StatusString.Queue, processing = Option.empty)
        data += res.id -> res
      }
  }

  override def contents(): Seq[Job] =
    data.values.seq.toList.sortBy(_.created.toEpochSecond(ZoneOffset.UTC))

  override def get(id: String): Option[Job] =
    data.get(id)

  override def log(id: String, offset: Int): Option[Seq[String]] = ???

  //    data
  //      .get(id)
  //      .map(job => Job(id, job.input, job.output, job.transtype, job.params, StatusString.Queue))

  override def add(newJob: Create): Job = {
    val job = newJob.toJob
    data += job.id -> job
    job
  }

  override def update(update: Update): Option[Job] = {
    data.get(update.id) match {
      case Some(job) => {
        val res = job.copy(status = update.status.getOrElse(job.status))
        data += res.id -> res
        Some(res)
      }
      case None => None
    }
  }

  override def request(transtypes: List[String]): Option[Job] = {
    data.values
      .filter(j => j.status == StatusString.Queue)
      .toList
      .sortWith(compare)
      .find(j => transtypes.contains(j.transtype)) match {
      case Some(job) => {
        val res = job.copy(status = StatusString.Process, processing = Some(LocalDateTime.now(clock)))
        data += res.id -> res
        Some(res)
      }
      case None => None
    }
  }

  private def compare(j: Job, k: Job): Boolean = {
    val p = j.priority.compareTo(k.priority)
    if (p != 0) {
      return p < 0
    }
    j.created.toEpochSecond(ZoneOffset.UTC).compareTo(k.created.toEpochSecond(ZoneOffset.UTC)) < 0
  }

  // FIXME this should return a Try or Option
  override def submit(result: JobResult): Job = {
    data.get(result.job.id) match {
      case Some(job) => {
        val res = job.copy(status = result.job.status, finished = Some(LocalDateTime.now(clock)))
        data += res.id -> res
        res
      }
      case None => result.job
    }
  }
}




