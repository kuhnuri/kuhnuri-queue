package controllers.v1

import javax.inject._
import models.request.JobResult
import filters.TokenAuthorizationFilter.WORKER_ATTR
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

  private val logger = Logger(this.getClass)

  def request = Action.async { request =>
    request.body.asJson.map { json =>
      json.validate[List[String]].map {
        case req: List[String] => {
          val worker = request.attrs(WORKER_ATTR)
          logger.debug("Request work for " + req.mkString(", "))
          Future {
            dispatcher.request(req, worker)
          }.map {
            case Some(job) =>
              logger.info(s"Requested work $job")
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
  }

  // FIXME this doesn't have to return anything, other than OK/NOK
  def submit = Action.async { request =>
//    logger.debug("Submit work")
    request.body.asJson.map { json =>
//      logger.debug(" submit work " + json.toString())
      json.validate[JobResult].map {
        case res: JobResult => {
//          logger.debug("  job result " + res)
          val worker = request.attrs(WORKER_ATTR)
          val submitter: String = res.task.worker.getOrElse(null)
          if (submitter == worker.id) {
//            logger.debug("Submit work for " + res.job)
            Future {
              dispatcher.submit(res)
            }.map {
              case res => Ok(Json.toJson(res))
            }
          } else {
            logger.error(s"Tried to submit other's work: ${submitter} != ${worker.id}")
            Future(Forbidden)
          }
        }
        case _ =>
          Future(InternalServerError)
      }.recoverTotal {
        e => {
          logger.error("Detected error B:" + JsError.toJson(e))
          Future(BadRequest("Detected error B:" + JsError.toJson(e)))
        }
      }
    }.getOrElse {
      Future(BadRequest("Expecting JSON data"))
    }
  }
}
