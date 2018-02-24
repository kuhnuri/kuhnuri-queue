package controllers.v1

import javax.inject._

import models._
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import services.Dispatcher

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Controller for worker communication API.
 */
@Singleton
class WorkController @Inject()(dispatcher: Dispatcher, cc: ControllerComponents) extends AbstractController(cc) {

  import controllers.v1.ListController._

  private val logger = Logger(this.getClass)

  def request = Action.async { request =>
    //    request.headers.get(AUTH_TOKEN_HEADER) match {
    //      case Some(token) if token == authToken => {
    logger.debug("request: " + request.body.asText)
    request.body.asJson.map { json =>
      json.validate[List[String]].map {
        case req: List[String] => {
          logger.debug("Request work for " + req.mkString(", "))
          Future {
            dispatcher.request(req)
          }.map {
            case Some(job) =>
              logger.info(s"Submit work $job")
              Ok(Json.toJson(job))
            case None => NoContent
          }
        }
      }.recoverTotal {
        e => {
          logger.error("Detected error A:" + JsError.toJson(e))
          Future(BadRequest("Detected error A:" + JsError.toJson(e)))
        }
      }
    }.getOrElse {
      Future(BadRequest("Expecting Json data"))
    }
    //      }
    //      case _ => Future(Unauthorized)
    //    }
  }

  // FIXME this doesn't have to return anything, other than OK/NOK
  def submit = Action.async { request =>
    //    request.headers.get(AUTH_TOKEN_HEADER) match {
    //      case Some(token) if token == authToken => {
    logger.debug("submit: " + request.body.asText)
    request.body.asJson.map { json =>
      logger.debug("submit: " + json.toString)
      json.validate[JobResult].map {
        case res: JobResult => {
          logger.debug("Submit work for " + res.job)
          Future {
            dispatcher.submit(res)
          }.map {
            case res => Ok(Json.toJson(res))
          }
        }
      }.recoverTotal {
        e => {
          logger.error("Detected error B:" + JsError.toJson(e))
          Future(BadRequest("Detected error B:" + JsError.toJson(e)))
        }
      }
    }.getOrElse {
      Future(BadRequest("Expecting Json data"))
    }
    //      }
    //      case _ => Future(Unauthorized)
    //    }
  }
}

object WorkController {
  //  implicit val jobRequestReads: Reads[JobRequest] = (
  //
  //  )(JobRequest.apply _)
  //    Reads[JobRequest](a => a.as[List[String]])
}

//sealed case class JobRequest(transtypes: List[String])