package models

import java.net.URI

import models.request.Register.uriReads
import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
  * Worker information.
  *
  * @param token worker authentication token
  * @param id    worker ID
  * @param uri   worker callback URI
  */
case class Worker(token: String, id: String, uri: URI)

object Worker {

  implicit val registerReads: Reads[Worker] = (
    (JsPath \ "token").read[String] and
      (JsPath \ "id").read[String] and
      (JsPath \ "uri").read[URI]
    ) (Worker.apply _)
}
