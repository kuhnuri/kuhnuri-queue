package models

import java.time.{LocalDateTime, ZoneOffset}

import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
  * Job storage.
  *
  * @param id        job ID
  * @param input     input file
  * @param output    output file
  * @param transtype transformation type
  */
sealed case class Job(id: String,
                      input: String,
                      output: String,
                      transtype: Seq[Task],
                      priority: Int,
                      created: LocalDateTime,
                      finished: Option[LocalDateTime],
                      status: StatusString)

object Job {

  import models.Task._

  implicit val localDateTimeWrites =
    Writes[LocalDateTime](s => JsString(s.atOffset(ZoneOffset.UTC).toString))

  implicit val jobWrites: Writes[Job] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "input").write[String] and
      (JsPath \ "output").write[String] and
      (JsPath \ "transtype").write[Seq[Task]] and
      (JsPath \ "priority").write[Int] and
      (JsPath \ "created").write[LocalDateTime] and
      (JsPath \ "finished").writeNullable[LocalDateTime] and
      (JsPath \ "status").write[StatusString]
    ) (unlift(Job.unapply _))
  implicit val jobReads: Reads[Job] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "input").read[String] /*.filter(new URI(_).isAbsolute)*/ and
      (JsPath \ "output").read[String] /*.filter(_.map {
        new URI(_).isAbsolute
      }.getOrElse(true))*/ and
      (JsPath \ "transtype").read[Seq[Task]] and
      (JsPath \ "priority").read[Int] and
      (JsPath \ "created").read[LocalDateTime] and
      (JsPath \ "finished").readNullable[LocalDateTime] and
      (JsPath \ "status").read[StatusString]
    ) (Job.apply _)

}
