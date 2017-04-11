package services

import java.time.ZoneOffset
import javax.inject.Singleton

import models._
import play.api.Logger

import scala.collection.mutable

@Singleton
class SimpleQueue extends Queue with Dispatcher {

  private val logger = Logger(this.getClass)

  val data: mutable.Map[String, JobRow] = mutable.Map()

  override def contents(): List[Job] =
    data.values.map(_.toJob).toList

  override def get(id: String): Option[Job] =
    data
      .get(id)
      .map(job => Job(id, job.input, job.output, job.transtype, job.params, StatusString.Queue))

  override def log(id: String, offset: Int): Option[Seq[String]] = ???

  //    data
  //      .get(id)
  //      .map(job => Job(id, job.input, job.output, job.transtype, job.params, StatusString.Queue))

  override def add(job: Create): Job = {
    val jobRow = JobRow(job)
    data += jobRow.id -> jobRow
    jobRow.toJob
  }

  override def update(update: Update): Option[Job] = {
    data.get(update.id) match {
      case Some(job) => {
        val res = job.copy(status = update.status.getOrElse(job.status))
        data += res.id -> res
        Some(res.toJob)
      }
      case None => None
    }
  }

  override def request(transtypes: List[String]): Option[Job] = {
    data.values
      .filter(j => j.status == StatusString.Queue)
      .toList
      .sortBy(j => j.created.toEpochSecond(ZoneOffset.UTC))
      .find(j => transtypes.contains(j.transtype)) match {
      case Some(job) => {
        val res = job.copy(status = StatusString.Process)
        data += res.id -> res
        Some(res.toJob)
      }
      case None => None
    }
  }

  // FIXME this should return a Try or Option
  override def submit(orig: JobResult): Job = {
    data.get(orig.job.id) match {
      case Some(job) => {
        val res = job.copy(status = StatusString.Done)
        data += res.id -> res
        res.toJob
      }
      case None => orig.job
    }
  }
}




