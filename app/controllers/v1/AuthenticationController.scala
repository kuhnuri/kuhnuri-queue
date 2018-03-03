package controllers.v1

import java.util.UUID

import filters.TokenAuthorizationFilter._
import javax.inject.Inject
import models._
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc._
import play.api.{Configuration, Logger}
import services.WorkerStore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class AuthenticationController @Inject()(configuration: Configuration,
                                         cc: ControllerComponents) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)
  private val users: Map[String, String] = configuration.get[Seq[Configuration]]("queue.users")
    .map(user => user.get[String]("username") -> user.get[String]("hash"))
    .toMap

  // FIXME check password to verify worker is authorized
  def login = Action.async { request =>
    request.body.asJson.map { json =>
      json.validate[Register] match {
        case req: JsSuccess[Register] => {
          val token: Try[String] = register(req.value)
          token match {
            case Success(token) =>
              Future(Ok.withHeaders(AUTH_TOKEN_HEADER -> token))
            case Failure(_) =>
              Future(Unauthorized)
          }
        }
        case e: JsError => Future(BadRequest("Detected error :" + JsError.toJson(e).toString))
      }
    }.getOrElse {
      Future(BadRequest("Expecting Json data"))
    }
  }

  private def register(reg: Register): Try[String] = {
    val hash: Option[String] = users.get(reg.id)
    if (hash.isEmpty) {
      return Failure(new UnauthorizedException)
    }
    if (!BCrypt.checkpw(reg.password, hash.get)) {
      return Failure(new UnauthorizedException)
    }
    val worker = Worker(UUID.randomUUID().toString, reg.id, reg.uri)
    logger.info(s"Register worker $reg with token ${worker.token}")
    WorkerStore.workers += worker.token -> worker
    logger.debug(s"Workers ${WorkerStore.workers}")
    return Success(worker.token)
  }

  def logout = Action.async { request =>
    request.headers.get(AUTH_TOKEN_HEADER) match {
      case Some(token) if WorkerStore.workers.get(token).isDefined =>
        logger.info(s"Unregister Worker with token $token")
        WorkerStore.workers -= token
        logger.debug(s"Workers ${WorkerStore.workers}")
        Future(Results.Ok)
      case Some(token) =>
        logger.info(s"Unrecognized API token $token")
        logger.debug(s"Workers ${WorkerStore.workers}")
        Future(Results.Unauthorized)
      case None =>
        logger.info("Missing API token")
        Future(Results.Unauthorized)
    }
  }

}

private case class UnauthorizedException() extends Exception
