package models

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

sealed case class JobResult(job: Job, log: Seq[String])

object JobResult {

  implicit val jobResultReads: Reads[JobResult] = (
    (JsPath \ "job").read[Job] and
      (JsPath \ "log").read[Seq[String]]
    ) (JobResult.apply _)

}
