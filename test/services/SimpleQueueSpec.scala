package services

import java.net.URI
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import models.request.JobResult
import models.{Job, StatusString, Task, Worker}
import org.scalatest._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

class SimpleQueueSpec extends FlatSpec with Matchers with BeforeAndAfter {

  private val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC.normalized())
  private val now = LocalDateTime.now(clock).atOffset(ZoneOffset.UTC)

  private val app = new GuiceApplicationBuilder()
    .configure(Map(
      "queue.temp" -> System.getProperty("java.io.tmpdir"),
      "queue.timeout" -> "10m",
      "queue.users" -> List(Map(
        "username" -> "worker",
        "hash" -> "$2a$10$c.9YXZkSrElx2dz8udP8vOlZSfF/ftsf4EClIORt8ILWd8vciLING"
      ))
    ))
    .overrides(
      bind(classOf[Dispatcher]).to(classOf[DummyQueue]),
      bind(classOf[Clock]).to(clock)
    )
    .build()

  private val queue = app.injector.instanceOf[Dispatcher].asInstanceOf[DummyQueue]
  private val worker = Worker("token", "worker-id", URI.create("worker-uri"))

  before {
    queue.data.clear()
  }

  "Empty queue" should "return nothing" in {
    queue.request(List("unsupported"), worker) match {
      case Some(_) => fail
      case None =>
    }
  }

  "Queue with finished items" should "return nothing" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/"), "html5", Map.empty,
          StatusString.Done, Some(queue.now.minusMinutes(10)), Some("worker-id"), Some(queue.now.minusMinutes(5)))
      ),
      0, queue.now.minusHours(1), Some(queue.now.minusMinutes(5)), StatusString.Done)

    queue.request(List("html5"), worker) match {
      case Some(res) => fail
      case None =>
    }
  }

  "Job with single task" should "return first task" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None, StatusString.Queue)

    queue.request(List("html5"), worker) match {
      case Some(res) => {
        res.transtype shouldBe "html5"
        res.id shouldBe "id-A_1"
        res.input shouldBe Some("file:/src/root.ditamap")
        res.output shouldBe Some("file:/dst/")
        res.worker shouldBe Some("worker-id")
        res.processing shouldBe Some(LocalDateTime.now(clock))
        res.status shouldBe StatusString.Process
      }
      case None => fail
    }
  }

  "Job with two tasks" should "return first task" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", None, None, "graphics", Map.empty, StatusString.Queue, None, None, None),
        Task("id-A_2", "id-A", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None, StatusString.Queue)

    queue.request(List("graphics"), worker) match {
      case Some(res) => {
        res.transtype shouldBe "graphics"
        res.id shouldBe "id-A_1"
        res.input shouldBe Some("file:/src/root.ditamap")
        res.output shouldBe None
        res.worker shouldBe Some("worker-id")
        res.processing shouldBe Some(LocalDateTime.now(clock))
        res.status shouldBe StatusString.Process
      }
      case None => fail
    }
  }

  "Job with one successful task" should "return second task" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/userguide.zip"),
          "html5", Map.empty, StatusString.Done, Some(queue.now.minusMinutes(10)), Some("worker-id"),
          Some(queue.now.minusMinutes(1))),
        Task("id-A_2", "id-A", None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None, StatusString.Process)

    queue.request(List("html5", "upload"), worker) match {
      case Some(res) => {
        res.transtype shouldBe "upload"
        res.id shouldBe "id-A_2"
        res.input shouldBe Some("file:/dst/userguide.zip")
        res.output shouldBe Some("file:/dst/")
        res.worker shouldBe Some("worker-id")
        res.processing shouldBe Some(LocalDateTime.now(clock))
        res.status shouldBe StatusString.Process
      }
      case None => fail
    }
  }

  "Job with single active task" should "accept job with update output" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/"), "html5", Map.empty,
          StatusString.Process, Some(queue.now), Some("worker-id"), None)
      ),
      0, queue.now.minusHours(1), None, StatusString.Process)

    val res = Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/userguide.zip"), "html5",
      Map.empty, StatusString.Done, Some(queue.now), Some("worker-id"), None)
    queue.submit(JobResult(res, List.empty))

    val job = queue.data("id-A")
    job.output shouldBe "file:/dst/userguide.zip"
    job.transtype.head.status shouldBe StatusString.Done
    job.transtype.head.output shouldBe Some("file:/dst/userguide.zip")
    job.finished shouldBe Some(LocalDateTime.now(clock))
    job.status shouldBe StatusString.Done
  }

  "Job with one active task" should "accept job with update output" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/"), "html5", Map.empty,
          StatusString.Process, Some(queue.now), Some("worker-id"), None),
        Task("id-A_2", "id-A", None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None, StatusString.Process)

    val res = Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/userguide.zip"),
      "html5", Map.empty, StatusString.Process, Some(queue.now), Some("worker-id"), None)
    queue.submit(JobResult(res, List.empty))

    val job = queue.data("id-A")
    job.output shouldBe "file:/dst/"
    job.transtype(0).status shouldBe StatusString.Process
    job.transtype(0).output shouldBe Some("file:/dst/userguide.zip")
    job.transtype(1).status shouldBe StatusString.Queue
    job.transtype(1).input shouldBe None
    job.transtype(1).output shouldBe None
    job.finished shouldBe None
    job.status shouldBe StatusString.Process
  }
}

