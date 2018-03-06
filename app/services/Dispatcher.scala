package services

import models._
import models.request.JobResult

/**
  * Communication interface for Workers.
  */
trait Dispatcher {
  /**
    * Request work
    *
    * @param transtypes list of transtypes worker support
    * @return job to perform
    */
  def request(transtypes: List[String], worker: Worker): Option[Job]

  /**
    * Submit work results
    *
    * @param job completed job
    */
  def submit(job: JobResult): Job
}
