package controllers.v1

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject._

import models._
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
