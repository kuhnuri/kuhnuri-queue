package services

import models._

trait Queue {
  def get(id: String): Option[Job]

  def log(id: String, offset: Int): Option[Seq[String]]

  def add(job: Create): Job

  def contents(): List[Job]

  def update(job: Update): Option[Job]

  //  def cancel(id: String): JobStatus
}
