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
    * @param worker     worker requesting work
    * @return task to perform
    */
  def request(transtypes: List[String], worker: Worker): Option[Task]

  /**
    * Submit work results
    *
    * @param job completed job
    */
  // TODO this should return a Try or Option
  def submit(job: JobResult): Task
}
