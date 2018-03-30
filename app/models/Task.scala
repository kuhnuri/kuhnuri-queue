package models

import java.time.{LocalDateTime, ZoneOffset}

import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
  * Task for a worker.
  *
  * @param id        job ID
  * @param input     input file
  * @param output    output file
  * @param transtype transformation type
  * @param params    DITA-OT parameters
  * @param status    status of the conversion
  */
sealed case class Task(id: String,
                       job: String,
                       input: Option[String],
                       output: Option[String],
                       transtype: String,
                       params: Map[String, String],
                       status: StatusString,
                       processing: Option[LocalDateTime],
                       worker: Option[String],
                       finished: Option[LocalDateTime])

object Task {

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

  implicit val taskWrites: Writes[Task] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "job").write[String] and
      (JsPath \ "input").writeNullable[String] and
      (JsPath \ "output").writeNullable[String] and
      (JsPath \ "transtype").write[String] and
      (JsPath \ "params").write[Map[String, String]] and
      (JsPath \ "status").write[StatusString] and
      (JsPath \ "processing").writeNullable[LocalDateTime] and
      (JsPath \ "worker").writeNullable[String] and
      (JsPath \ "finished").writeNullable[LocalDateTime]
    ) (unlift(Task.unapply _))
  implicit val taskReads: Reads[Task] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "job").read[String] and
      (JsPath \ "input").readNullable[String] /*.filter(new URI(_).isAbsolute)*/ and
      (JsPath \ "output").readNullable[String] /*.filter(_.map {
        new URI(_).isAbsolute
      }.getOrElse(true))*/ and
      (JsPath \ "transtype").read[String] and
      (JsPath \ "params").read[Map[String, String]] and
      (JsPath \ "status").read[StatusString] and
      (JsPath \ "processing").readNullable[LocalDateTime] and
      (JsPath \ "worker").readNullable[String] and
      (JsPath \ "finished").readNullable[LocalDateTime]
    ) (Task.apply _)

}

