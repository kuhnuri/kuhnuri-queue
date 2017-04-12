package models

import java.net.URI

import play.api.libs.functional.syntax._
import play.api.libs.json._
import models.Register.uriReads

case class Worker(token: String, id: String, uri: URI)

object Worker {

  implicit val registerReads: Reads[Worker] = (
    (JsPath \ "token").read[String] and
      (JsPath \ "id").read[String] and
      (JsPath \ "uri").read[URI]
    ) (Worker.apply _)
}
