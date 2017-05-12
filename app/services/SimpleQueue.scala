package services

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.Singleton

import models._
import play.api.Logger

import scala.collection.mutable

@Singleton
class SimpleQueue extends Queue with Dispatcher {

  private val logger = Logger(this.getClass)

  val data: mutable.Map[String, Job] = mutable.Map()

  override def contents(): List[Job] =
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
        val res = job.copy(status = StatusString.Process, processing = Some(LocalDateTime.now(ZoneOffset.UTC)))
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
        val res = job.copy(status = result.job.status, finished = Some(LocalDateTime.now(ZoneOffset.UTC)))
        data += res.id -> res
        res
      }
      case None => result.job
    }
  }
}




