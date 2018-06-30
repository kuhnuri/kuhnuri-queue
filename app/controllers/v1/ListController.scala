package controllers.v1

import javax.inject._
import models._
import models.request.{Create, Filter}
import models.response.Error
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import services.Queue

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Controller for queue client communication
  */
@Singleton
class ListController @Inject()(queue: Queue, cc: ControllerComponents) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  def list(status: Option[String]) = Action {
    try {
      val filter = Filter(status.map(StatusString.apply))

      val queueList = queue.contents(filter)

      Ok(Json.toJson(queueList))
    } catch {
      case _: IllegalArgumentException => BadRequest(Json.toJson(Error("ERR001")))
    }
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
        _ => Future(BadRequest(Json.toJson(Error("ERR003"))))
      }
    }.getOrElse {
      Future(BadRequest(Json.toJson(Error("ERR002"))))
    }
  }

}
