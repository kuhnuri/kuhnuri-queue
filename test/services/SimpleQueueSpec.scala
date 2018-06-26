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

  "Job with one active task" should "not return second task" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/"), "html5", Map.empty,
          StatusString.Process, Some(queue.now.minusMinutes(10)), Some("worker-id"), None),
        Task("id-A_2", "id-A", None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None, StatusString.Process)

    queue.request(List("upload"), worker) match {
      case Some(_) => fail
      case None =>
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

  "Job with first active task" should "accept job with update output" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), None, "graphics", Map.empty, StatusString.Process,
          Some(queue.now.minusMinutes(3)), Some("worker-id"), None),
        Task("id-A_2", "id-A", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None, StatusString.Process)

    val res = Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/tmp/userguide.zip"),
      "html5", Map.empty, StatusString.Done, Some(queue.now.minusMinutes(1)), Some("worker-id"), Some(queue.now))
    queue.submit(JobResult(res, List.empty))

    val job = queue.data("id-A")
    job.output shouldBe "file:/dst/"
    job.transtype(0).status shouldBe StatusString.Done
    job.transtype(0).input shouldBe Some("file:/src/root.ditamap")
    job.transtype(0).output shouldBe Some("file:/tmp/userguide.zip")
    job.transtype(1).status shouldBe StatusString.Queue
    job.transtype(1).input shouldBe None
    job.transtype(1).output shouldBe None
    job.finished shouldBe None
    job.status shouldBe StatusString.Process
  }

  "Job with second active task" should "accept job with update output" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/userguide.zip"),
          "graphics", Map.empty, StatusString.Done, Some(queue.now.minusMinutes(3)), Some("worker-id"),
          Some(queue.now.minusMinutes(2))),
        Task("id-A_2", "id-A", Some("file:/dst/userguide.zip"), Some("file:/dst/"), "html5", Map.empty,
          StatusString.Process, Some(queue.now.minusMinutes(1)), Some("worker-id"), None)
      ),
      0, queue.now.minusHours(1), None, StatusString.Process)

    val res = Task("id-A_2", "id-A", Some("file:/dst/userguide.zip"), Some("file:/dst/"),
      "html5", Map.empty, StatusString.Done, Some(queue.now.minusMinutes(1)), Some("worker-id"), Some(queue.now))
    queue.submit(JobResult(res, List.empty))

    val job = queue.data("id-A")
    job.output shouldBe "file:/dst/"
    job.transtype(0).status shouldBe StatusString.Done
    job.transtype(0).output shouldBe Some("file:/dst/userguide.zip")
    job.transtype(1).status shouldBe StatusString.Done
    job.transtype(1).input shouldBe Some("file:/dst/userguide.zip")
    job.transtype(1).output shouldBe Some("file:/dst/")
    job.finished shouldBe Some(queue.now)
    job.status shouldBe StatusString.Done
  }

  "Job with last active task" should "accept job with update output" in {
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

  "Submit with new task parameters" should "be added to work parameters" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/src/root.ditamap",
      "file:/dst/",
      List(
        Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/"), "html5",
          Map("a" -> "A", "b" -> "B"),
          StatusString.Process, Some(queue.now.minusHours(1)), Some("worker-id"), None)
      ),
      0, queue.now.minusHours(2), None, StatusString.Process)

    val res = Task("id-A_1", "id-A", Some("file:/src/root.ditamap"), Some("file:/dst/userguide.zip"), "html5",
      Map("b" -> "C", "d" -> "D"), StatusString.Done, Some(queue.now.minusHours(1)), Some("worker-id"),
      Some(queue.now))
    queue.submit(JobResult(res, List.empty))

    val job = queue.data("id-A")
    job.transtype(0).params shouldBe Map("a" -> "A", "b" -> "C", "d" -> "D")
  }

  def createTask(statuses: StatusString*): Seq[Task] =
    statuses.map(Task(null, null, Some(null), null, null, null, _, null, null, null))

  "Get status from tasks" should "return queue" in {
    queue.getStatus(createTask(StatusString.Queue, StatusString.Queue)) shouldBe StatusString.Queue
  }

  it should "return process" in {
    queue.getStatus(createTask(StatusString.Done, StatusString.Process)) shouldBe StatusString.Process
  }

  it should "return error" in {
    queue.getStatus(createTask(StatusString.Done, StatusString.Error)) shouldBe StatusString.Error
  }

  it should "return done" in {
    queue.getStatus(createTask(StatusString.Done, StatusString.Done)) shouldBe StatusString.Done
  }
}

