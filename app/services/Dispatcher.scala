package services

import models._
import models.request.JobResult

/**
  * Communication interface for Workers.
  */
trait Dispatcher {
  // client methods
  //  def status(id: String): Option[JobStatus]
  //
  //  def create(job: Create): Job
  //
  //  def list(): List[Job]

  // worker methods
  /**
    * Request work
    *
    * @param transtypes list of transtypes worker support
    * @return job to perform
    */
  def request(transtypes: List[String]): Option[Job]

  /**
    * Submit work results
    *
    * @param job completed job
    */
  def submit(job: JobResult): Job
}

//class DefaultDispatcher @Inject()(queue: Queue) extends Dispatcher {
////  override def status(id: String): Option[JobStatus] = queue.get(id)
////
////  override def create(job: Create): Job = queue.add(job)
////
////  override def list(): List[Job] = queue.contents()
//
//  def request(transtypes: List[String]): Option[Job] = {
//    queue.contents()
//      .find(j => transtypes.contains(j.transtype)) match {
//      case Some(job) => {
//        val res = Update(job.id, Some(StatusString.Process))
//        queue.update(res)
//        Some(job)
//      }
//      case None => None
//    }
//  }
//
//  // FIXME this should return a Try or Option
//  def submit(orig: Job): Job = {
//    queue.get(orig.id) match {
//      case Some(job) => {
//        val res = Update(job.id, Some(job.status))
//        val resJob = queue.update(res)
//        resJob match {
//          case Some(r) => r
//          case None => orig
//        }
//      }
//      case None => orig
//    }
//  }
//}
