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

  val now = LocalDateTime.now(clock)

//  val dummyData: Map[String, Job] = List(
//    Job("id-A",
//      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
//      "file:/Volumes/tmp/out/",
//      List(
//        Task("id-A_1",
//          "id-A",
//          None,
//          None,
//          "html5",
//          Map.empty,
//          StatusString.Queue,
//          None,
//          None,
//          None
//        )
//      ),
//      0,
//      now.minusHours(1),
//      None
//    ),
//    Job("id-B",
//      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
//      "file:/Volumes/tmp/out/",
//      List(
//        Task("id-B_1",
//          "id-B",
//          None,
//          None,
//          "html5",
//          Map.empty,
//          StatusString.Queue,
//          None,
//          None,
//          None
//        ),
//        Task("id-B_2",
//          "id-B",
//          None,
//          None,
//          "upload",
//          Map.empty,
//          StatusString.Queue,
//          None,
//          None,
//          None
//        )
//      ),
//      0,
//      now.minusHours(2),
//      None
//    ),
//    Job("id-C",
//      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
//      "file:/Volumes/tmp/out/",
//      List(
//        Task("id-C_1",
//          "id-C",
//          None,
//          None,
//          "pdf",
//          Map.empty,
//          StatusString.Queue,
//          None,
//          None,
//          None
//        )
//      ),
//      0,
//      now,
//      None
//    ),
//  ).map {
//    job: Job => (job.id, job)
//  }.toMap

  override val data: mutable.Map[String, Job] = mutable.Map(
//    dummyData.toSeq: _*
  )
}
