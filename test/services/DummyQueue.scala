package services

import java.time.LocalDateTime
import javax.inject.Singleton

import models._

import scala.collection.mutable

@Singleton
class DummyQueue extends SimpleQueue {
  override val data: mutable.Map[String, JobRow] = mutable.Map(
    "id-A" -> JobRow("id-A",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out",
      "html5",
      Map.empty,
      StatusString.Queue,
      LocalDateTime.now.minusHours(1)),
    "id-A1" -> JobRow("id-A1",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out",
      "html5",
      Map.empty,
      StatusString.Queue,
      LocalDateTime.now.minusHours(2)),
    "id-B" -> JobRow("id-B",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out",
      "pdf",
      Map.empty,
      StatusString.Queue,
      LocalDateTime.now)
  )
}
