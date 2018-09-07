package controllers.v1

import java.util.UUID

import filters.TokenAuthorizationFilter._
import javax.inject.Inject
import models._
import models.request.Register
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

  def login = Action.async { request =>
    request.body.asJson.map { json =>
      json.validate[Register] match {
        case req: JsSuccess[Register] => {
          logger.info(s"Attempting to register ${req.value.id}")
          val token: Try[String] = register(req.value)
          token match {
            case Success(token) =>
              logger.info(s"Registered worker ${req.value.id}")
              Future(Ok.withHeaders(AUTH_TOKEN_HEADER -> token))
            case Failure(_) =>
              logger.info(s"Failed to register worker ${req.value.id}")
              Future(Unauthorized)
          }
        }
        case e: JsError => Future(BadRequest("Detected error :" + JsError.toJson(e).toString))
      }
    }.getOrElse {
      logger.error("Expected JSON data")
      Future(BadRequest("Expecting Json data"))
    }
  }

  private def register(reg: Register): Try[String] = {
    val hash: Option[String] = users.get(reg.id)
    if (hash.isEmpty) {
      logger.error("No user found " + reg.id)
      return Failure(new UnauthorizedException)
    }
    if (!BCrypt.checkpw(reg.password, hash.get)) {
      logger.error("Password doesn't match " + reg.password + " " + hash.get)
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
