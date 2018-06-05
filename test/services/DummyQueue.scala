package services

import java.time.{Clock, LocalDateTime}

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

@Singleton
class DummyQueue @Inject()(ws: WSClient,
                           configuration: Configuration,
                           clock: Clock,
                           actorSystem: ActorSystem)(implicit executionContext: ExecutionContext)
  extends SimpleQueue(ws, configuration, clock, actorSystem) {

  val now = LocalDateTime.now(clock)
}
