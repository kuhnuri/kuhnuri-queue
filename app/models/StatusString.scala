package models

import generated.enums.Status
import play.api.libs.json._

sealed trait StatusString

object StatusString {

  case object Queue extends StatusString {
    override val toString = "queue"
  }

  case object Process extends StatusString {
    override val toString = "process"
  }

  case object Done extends StatusString {
    override val toString = "done"
  }

  case object Error extends StatusString {
    override val toString = "error"
  }

  def apply(status: String): StatusString = status match {
    case "queue" => Queue
    case "process" => Process
    case "done" => Done
    case "error" => Error
    case s: String => throw new IllegalArgumentException(s)
  }

  def parse(status: Status): StatusString = status match {
    case Status.queue => Queue
    case Status.process => Process
    case Status.done => Done
    case Status.error => Error
  }

  implicit val jobStatusStringReads =
    Reads[StatusString](j => try {
      JsSuccess(StatusString(j.as[JsString].value))
    } catch {
      case e: IllegalArgumentException => JsError(e.toString)
    })

  implicit val jobStatusStringWrites =
    Writes[StatusString](s => JsString(s.toString))

}
