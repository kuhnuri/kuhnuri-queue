package models.request

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID

import models.{Job, StatusString}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

sealed case class Create(input: String, output: String, transtype: String, priority: Option[Int], params: Map[String, String]) {
  def toJob: Job =
    Job(UUID.randomUUID().toString, this.input, this.output, this.transtype, this.params,
      StatusString.Queue, priority.getOrElse(0), LocalDateTime.now(ZoneOffset.UTC), None, None)
}

object Create {

  implicit val createReads: Reads[Create] = (
    (JsPath \ "input").read[String] /*.filter(new URI(_).isAbsolute)*/ and
      (JsPath \ "output").read[String] /*.filter(_.map {
        new URI(_).isAbsolute
      }.getOrElse(true))*/ and
      (JsPath \ "transtype").read[String] and
      (JsPath \ "priority").readNullable[Int] and
      (JsPath \ "params").read[Map[String, String]]
    ) (Create.apply _)

}