package services

import java.time.{Clock, LocalDateTime}
import javax.inject.{Inject, Singleton}

import scala.collection.mutable

import models._

@Singleton
class DummyQueue @Inject()(clock: Clock) extends SimpleQueue(clock) {
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
