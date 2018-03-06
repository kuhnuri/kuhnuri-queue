package models

import java.time.{LocalDateTime, ZoneOffset}

import generated.enums.Status
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
  * @param params    DITA-OT parameters
  * @param status    status of the conversion
  */
sealed case class Job(id: String,
                      input: String,
                      output: String,
                      transtype: String,
                      params: Map[String, String],
                      status: StatusString,
                      priority: Int,
                      created: LocalDateTime,
                      processing: Option[LocalDateTime],
                      worker: Option[String],
                      finished: Option[LocalDateTime])

object Job {

  implicit val jobStatusStringReads =
    Reads[StatusString](j => try {
      JsSuccess(StatusString.parse(j.as[JsString].value))
    } catch {
      case e: IllegalArgumentException => JsError(e.toString)
    })

  implicit val jobStatusStringWrites =
    Writes[StatusString](s => JsString(s.toString))

  implicit val localDateTimeWrites =
    Writes[LocalDateTime](s => JsString(s.atOffset(ZoneOffset.UTC).toString))

  implicit val jobWrites: Writes[Job] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "input").write[String] and
      (JsPath \ "output").write[String] and
      (JsPath \ "transtype").write[String] and
      (JsPath \ "params").write[Map[String, String]] and
      (JsPath \ "status").write[StatusString] and
      (JsPath \ "priority").write[Int] and
      (JsPath \ "created").write[LocalDateTime] and
      (JsPath \ "processing").writeNullable[LocalDateTime] and
      (JsPath \ "worker").writeNullable[String] and
      (JsPath \ "finished").writeNullable[LocalDateTime]
    ) (unlift(Job.unapply _))
  implicit val jobReads: Reads[Job] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "input").read[String] /*.filter(new URI(_).isAbsolute)*/ and
      (JsPath \ "output").read[String] /*.filter(_.map {
        new URI(_).isAbsolute
      }.getOrElse(true))*/ and
      (JsPath \ "transtype").read[String] and
      (JsPath \ "params").read[Map[String, String]] and
      (JsPath \ "status").read[StatusString] and
      (JsPath \ "priority").read[Int] and
      (JsPath \ "created").read[LocalDateTime] and
      (JsPath \ "processing").readNullable[LocalDateTime] and
      (JsPath \ "worker").readNullable[String] and
      (JsPath \ "finished").readNullable[LocalDateTime]
    ) (Job.apply _)

}
