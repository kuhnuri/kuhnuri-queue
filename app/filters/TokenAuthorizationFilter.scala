package filters

import akka.stream.Materializer
import javax.inject._
import models.Worker
import play.api.Logger
import play.api.libs.typedmap.{TypedKey, TypedMap}
import play.api.mvc._
import services.WorkerStore

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TokenAuthorizationFilter @Inject()(
                                          implicit override val mat: Materializer,
                                          exec: ExecutionContext) extends Filter {

  import TokenAuthorizationFilter._

  private val logger = Logger(this.getClass)

  override def apply(nextFilter: RequestHeader => Future[Result])
                    (requestHeader: RequestHeader): Future[Result] = {
    // FIXME all paths should be authorized, not just worker routes
    if (requestHeader.path.startsWith("/api/v1/work")) {
      requestHeader.headers.get(AUTH_TOKEN_HEADER) match {
        case Some(token) =>
          WorkerStore.workers.get(token) match {
            case Some(worker) =>
              val attrs: TypedMap = requestHeader.attrs + (WORKER_ATTR -> worker)
              nextFilter(requestHeader.withAttrs(attrs))
            case None =>
              logger.info(s"Unrecognized API token $token")
              logger.info(s"Workers ${WorkerStore.workers}")
              Future(Results.Unauthorized)
          }
        case None =>
          logger.info("Missing API token")
          Future(Results.Unauthorized)
      }
    } else {
      nextFilter(requestHeader)
    }
  }
}

object TokenAuthorizationFilter {
  val AUTH_TOKEN_HEADER = "X-Auth-Token"
  val WORKER_ATTR = TypedKey[Worker]("worker")
}