package models.response

import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._

case class Error(code: String, message: String)

object Error {

  private val errors: Map[String, Error] = List[Error](
    Error("ERR001", "Invalid argument"),
    Error("ERR002", "Body should be a JSON object"),
    Error("ERR003", "Invalid JSON")
  ).map(error => (error.code, error)).toMap

  def apply(code: String) = errors(code)

  implicit val errorWrites: Writes[Error] = (
    (JsPath \ "code").write[String] and
      (JsPath \ "message").write[String]
    ) (unlift(Error.unapply _))
}