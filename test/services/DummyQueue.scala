package services

import java.time.{Clock, LocalDateTime}

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import models._
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.collection.mutable
import scala.concurrent.ExecutionContext

@Singleton
class DummyQueue @Inject()(ws: WSClient,
                           configuration: Configuration,
                           clock: Clock,
                           actorSystem: ActorSystem)(implicit executionContext: ExecutionContext)
  extends SimpleQueue(ws, configuration, clock, actorSystem) {

  private val now = LocalDateTime.now(clock)
  override val data: mutable.Map[String, Job] = mutable.Map(
    "id-A" -> Job("id-A",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out/",
      "html5",
      Map.empty,
      StatusString.Queue,
      0,
      now.minusHours(1),
      None,
      None
    ),
    "id-A1" -> Job("id-A1",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out/",
      "html5",
      Map.empty,
      StatusString.Queue,
      0,
      now.minusHours(2),
      None,
      None
    ),
    "id-B" -> Job("id-B",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out/",
      "pdf",
      Map.empty,
      StatusString.Queue,
      0,
      now,
      None,
      None
    )
  )
}
