package controllers.v1

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject._

import models._
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc._
import services.Queue

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Controller for queue client communication
  */
@Singleton
class ListController @Inject()(queue: Queue) extends Controller {

  import ListController._

  private val logger = Logger(this.getClass)

  def list = Action {
    val queueList = queue.contents

    Ok(Json.toJson(queueList))
  }

  def details(id: String) = Action {
    queue.get(id) match {
      case Some(job) => Ok(Json.toJson(job))
      case None => NotFound
    }
  }

  def log(id: String, offset: Int) = Action {
    queue.log(id, offset) match {
      case Some(log) => Ok(Json.toJson(log))
      case None => NotFound
    }
  }

  def add = Action.async { request =>
    request.body.asJson.map { json =>
      json.validate[Create].map {
        case job: Create => {
          Future {
            queue.add(job)
          }.map {
            j: Job => Created(Json.toJson(j))
          }
        }
      }.recoverTotal {
        e => Future(BadRequest("Detected error:" + JsError.toJson(e)))
      }
    }.getOrElse {
      Future(BadRequest("Expecting Json data"))
    }
  }

}

object ListController {
  implicit val localDateTimeWrites =
    Writes[LocalDateTime](s => JsString(s.atOffset(ZoneOffset.UTC).toString))


  implicit val jobStatusStringReads =
    Reads[StatusString](j => try {
      JsSuccess(StatusString.parse(j.as[JsString].value))
    } catch {
      case e: IllegalArgumentException => JsError(e.toString)
    })

  implicit val jobStatusStringWrites =
    Writes[StatusString](s => JsString(s.toString))

  implicit val createReads: Reads[Create] = (
    (JsPath \ "input").read[String] /*.filter(new URI(_).isAbsolute)*/ and
      (JsPath \ "output").read[String] /*.filter(_.map {
        new URI(_).isAbsolute
      }.getOrElse(true))*/ and
      (JsPath \ "transtype").read[String] and
      (JsPath \ "priority").readNullable[Int] and
      (JsPath \ "params").read[Map[String, String]]
    ) (Create.apply _)

  implicit val jobWrites: Writes[Job] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "input").write[String] and
      (JsPath \ "output").write[String] and
      (JsPath \ "transtype").write[String] and
      (JsPath \ "params").write[Map[String, String]] and
      (JsPath \ "status").write[StatusString] and
      (JsPath \ "priority").write[Int] and
      (JsPath \ "created").write[LocalDateTime] and
      (JsPath \ "processing").write[Option[LocalDateTime]] and
      (JsPath \ "finished").write[Option[LocalDateTime]]
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
      (JsPath \ "finished").readNullable[LocalDateTime]
    ) (Job.apply _)

  implicit val jobResultReads: Reads[JobResult] = (
    (JsPath \ "job").read[Job] and
      (JsPath \ "log").read[Seq[String]]
    ) (JobResult.apply _)

  //  implicit val jobStatusWrites: Writes[JobStatus] = (
  //    (JsPath \ "id").write[String] and
  //      (JsPath \ "output").writeNullable[String] and
  //      (JsPath \ "status").write[StatusString]
  //    ) (unlift(JobStatus.unapply _))
}
