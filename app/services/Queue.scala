package services

import models._
import models.request.Create

trait Queue {
  def get(id: String): Option[Job]

  def log(id: String, offset: Int): Option[Seq[String]]

  def add(job: Create): Job

  def contents(): Seq[Job]

  def update(job: Update): Option[Job]

  //  def cancel(id: String): JobStatus
}
