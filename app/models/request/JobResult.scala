package models.request

import models.Task
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

sealed case class JobResult(task: Task, log: Seq[String])

object JobResult {

  implicit val jobResultReads: Reads[JobResult] = (
    (JsPath \ "task").read[Task] and
      (JsPath \ "log").read[Seq[String]]
    ) (JobResult.apply _)

}
