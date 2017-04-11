package models

import java.net.URI

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Worker(token: String, uri: URI)

object Worker {

  def parse(token: String, uri: String) = Worker(token, new URI(uri))

  implicit val registerReads: Reads[Worker] = (
    (JsPath \ "token").read[String] and
      (JsPath \ "uri").read[String]
    ) (Worker.parse _)
}

case class Register(uri: URI)

object Register {

  def parse(uri: String) = Register(new URI(uri))

  implicit val registerReads: Reads[Register] =
    (__ \ "uri").read[String]
      .map(v => Register.parse(v))
}