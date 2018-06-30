package services

import models._
import models.request.{Create, Filter}

trait Queue {
  /** Get job from queue. */
  def get(id: String): Option[Job]

  /** Get log for a job. */
  def log(id: String, offset: Int): Option[Seq[String]]

  /** Add a new job into the queue. */
  def add(job: Create): Job

  /** List the contents of the queue. */
  def contents(filter: Filter): Seq[Job]

//  /** Update job status in queue. */
//  def update(job: Update): Option[Task]

  //  def cancel(id: String): JobStatus
}
