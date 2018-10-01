package services

import java.net.URI
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import models.request.JobResult
import models.{Job, StatusString, Task, Worker}
import org.scalatest._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

class SimpleQueueSpec extends FlatSpec with Matchers with BeforeAndAfter {

  private val IN_URL = "file://in"
  private val OUT_URL = "file://out"
  private val JOB_A = "id-A"
  private val TASK_A = "id-A_1"
  private val TASK_B = "id-A_2"
  private val WORKER_ID = "worker-id"

  private val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC.normalized())
  private val now = LocalDateTime.now(clock)//.atOffset(ZoneOffset.UTC)

  private val app = new GuiceApplicationBuilder()
    .configure(Map(
      "queue.data" -> System.getProperty("java.io.tmpdir"),
      "queue.timeout" -> "10m",
      "queue.users" -> List(Map(
        "username" -> "worker",
        "hash" -> "$2a$10$c.9YXZkSrElx2dz8udP8vOlZSfF/ftsf4EClIORt8ILWd8vciLING"
      )),
      "queue.alias" -> List(
        Map(
          "name" -> "duo",
          "transtypes" -> List("first", "second"),
          "params" -> Map(
            "foo" -> "bar"
          )
        ),
        Map(
          "name" -> "single",
          "transtypes" -> List("replacement"),
          "params" -> Map(
            "foo" -> "override",
            "bar" -> "baz"
          )
        )
      )
    ))
    .overrides(
      bind(classOf[Dispatcher]).to(classOf[DummyQueue]),
      bind(classOf[Clock]).to(clock)
    )
    .build()

  private val queue = app.injector.instanceOf[Dispatcher].asInstanceOf[DummyQueue]
  private val worker = Worker("token", WORKER_ID, URI.create("worker-uri"))

  before {
    queue.data.clear()
  }

  // Dispatcher

  "Empty queue" should "return nothing" in {
    queue.request(List("unsupported"), worker) match {
      case Some(_) => fail
      case None =>
    }
  }

  "Queue with finished items" should "return nothing" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5", Map.empty,
          StatusString.Done, Some(now.minusMinutes(10)), Some(WORKER_ID), Some(now.minusMinutes(5)))
      ),
      0, now.minusHours(1), Some(now.minusMinutes(5)), StatusString.Done)
    )

    queue.request(List("html5"), worker) match {
      case Some(res) => fail
      case None =>
    }
  }

  "Job with single task" should "return first task" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, now.minusHours(1), None, StatusString.Queue)
    )

    queue.request(List("html5"), worker) match {
      case Some(res) => {
        res.transtype shouldBe "html5"
        res.id shouldBe TASK_A
        res.input shouldBe Some(IN_URL)
        res.output shouldBe Some(OUT_URL)
        res.worker shouldBe Some(WORKER_ID)
        res.processing shouldBe Some(LocalDateTime.now(clock))
        res.status shouldBe StatusString.Process
      }
      case None => fail
    }
  }

  "Job with two tasks" should "return first task" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, None, None, "graphics", Map.empty, StatusString.Queue, None, None, None),
        Task(TASK_B, JOB_A, None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, now.minusHours(1), None, StatusString.Queue)
    )

    queue.request(List("graphics"), worker) match {
      case Some(res) => {
        res.transtype shouldBe "graphics"
        res.id shouldBe TASK_A
        res.input shouldBe Some(IN_URL)
        res.output shouldBe None
        res.worker shouldBe Some(WORKER_ID)
        res.processing shouldBe Some(LocalDateTime.now(clock))
        res.status shouldBe StatusString.Process
      }
      case None => fail
    }
  }

  "Job with one successful task" should "return second task" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, Some(IN_URL), Some("file:/dst/userguide.zip"),
          "html5", Map.empty, StatusString.Done, Some(now.minusMinutes(10)), Some(WORKER_ID),
          Some(now.minusMinutes(1))),
        Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, now.minusHours(1), None, StatusString.Process)
    )

    queue.request(List("html5", "upload"), worker) match {
      case Some(res) => {
        res.transtype shouldBe "upload"
        res.id shouldBe TASK_B
        res.input shouldBe Some("file:/dst/userguide.zip")
        res.output shouldBe Some(OUT_URL)
        res.worker shouldBe Some(WORKER_ID)
        res.processing shouldBe Some(LocalDateTime.now(clock))
        res.status shouldBe StatusString.Process
      }
      case None => fail
    }
  }

  "Job with one active task" should "not return second task" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5", Map.empty,
          StatusString.Process, Some(now.minusMinutes(10)), Some(WORKER_ID), None),
        Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, now.minusHours(1), None, StatusString.Process)
    )

    queue.request(List("upload"), worker) match {
      case Some(_) => fail
      case None =>
    }
  }

  "Job with single active task" should "accept job with update output" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5", Map.empty,
          StatusString.Process, Some(now), Some(WORKER_ID), None)
      ),
      0, now.minusHours(1), None, StatusString.Process)
    )

    val res = Task(TASK_A, JOB_A, Some(IN_URL), Some("file:/dst/userguide.zip"), "html5",
      Map.empty, StatusString.Done, Some(now), Some(WORKER_ID), None)
    queue.submit(JobResult(res, List.empty))

    val job = queue.data(JOB_A)
    job.output shouldBe "file:/dst/userguide.zip"
    job.transtype.head.status shouldBe StatusString.Done
    job.transtype.head.output shouldBe Some("file:/dst/userguide.zip")
    job.finished shouldBe Some(LocalDateTime.now(clock))
    job.status shouldBe StatusString.Done
  }

  "Job with first active task" should "accept job with update output" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, Some(IN_URL), None, "graphics", Map.empty, StatusString.Process,
          Some(now.minusMinutes(3)), Some(WORKER_ID), None),
        Task(TASK_B, JOB_A, None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, now.minusHours(1), None, StatusString.Process)
    )

    val res = Task(TASK_A, JOB_A, Some(IN_URL), Some("file:/tmp/userguide.zip"),
      "html5", Map.empty, StatusString.Done, Some(now.minusMinutes(1)), Some(WORKER_ID), Some(now))
    queue.submit(JobResult(res, List.empty))

    val job = queue.data(JOB_A)
    job.output shouldBe OUT_URL
    job.transtype(0).status shouldBe StatusString.Done
    job.transtype(0).input shouldBe Some(IN_URL)
    job.transtype(0).output shouldBe Some("file:/tmp/userguide.zip")
    job.transtype(1).status shouldBe StatusString.Queue
    job.transtype(1).input shouldBe None
    job.transtype(1).output shouldBe None
    job.finished shouldBe None
    job.status shouldBe StatusString.Process
  }

  "Job with second active task" should "accept job with update output" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, Some(IN_URL), Some("file:/dst/userguide.zip"),
          "graphics", Map.empty, StatusString.Done, Some(now.minusMinutes(3)), Some(WORKER_ID),
          Some(now.minusMinutes(2))),
        Task(TASK_B, JOB_A, Some("file:/dst/userguide.zip"), Some(OUT_URL), "html5", Map.empty,
          StatusString.Process, Some(now.minusMinutes(1)), Some(WORKER_ID), None)
      ),
      0, now.minusHours(1), None, StatusString.Process)
    )

    val res = Task(TASK_B, JOB_A, Some("file:/dst/userguide.zip"), Some(OUT_URL),
      "html5", Map.empty, StatusString.Done, Some(now.minusMinutes(1)), Some(WORKER_ID), Some(now))
    queue.submit(JobResult(res, List.empty))

    val job = queue.data(JOB_A)
    job.output shouldBe OUT_URL
    job.transtype(0).status shouldBe StatusString.Done
    job.transtype(0).output shouldBe Some("file:/dst/userguide.zip")
    job.transtype(1).status shouldBe StatusString.Done
    job.transtype(1).input shouldBe Some("file:/dst/userguide.zip")
    job.transtype(1).output shouldBe Some(OUT_URL)
    job.finished shouldBe Some(now)
    job.status shouldBe StatusString.Done
  }

  "Job with last active task" should "accept job with update output" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5", Map.empty,
          StatusString.Process, Some(now), Some(WORKER_ID), None),
        Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, now.minusHours(1), None, StatusString.Process)
    )

    val res = Task(TASK_A, JOB_A, Some(IN_URL), Some("file:/dst/userguide.zip"),
      "html5", Map.empty, StatusString.Process, Some(now), Some(WORKER_ID), None)
    queue.submit(JobResult(res, List.empty))

    val job = queue.data(JOB_A)
    job.output shouldBe OUT_URL
    job.transtype(0).status shouldBe StatusString.Process
    job.transtype(0).output shouldBe Some("file:/dst/userguide.zip")
    job.transtype(1).status shouldBe StatusString.Queue
    job.transtype(1).input shouldBe None
    job.transtype(1).output shouldBe None
    job.finished shouldBe None
    job.status shouldBe StatusString.Process
  }

  "Submit with new task parameters" should "be added to work parameters" in {
    insertJob(Job(JOB_A, IN_URL, OUT_URL,
      List(
        Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5",
          Map("a" -> "A", "b" -> "B"),
          StatusString.Process, Some(now.minusHours(1)), Some(WORKER_ID), None)
      ),
      0, now.minusHours(2), None, StatusString.Process)
    )

    val res = Task(TASK_A, JOB_A, Some(IN_URL), Some("file:/dst/userguide.zip"), "html5",
      Map("b" -> "C", "d" -> "D"), StatusString.Done, Some(now.minusHours(1)), Some(WORKER_ID),
      Some(now))
    queue.submit(JobResult(res, List.empty))

    val job = queue.data(JOB_A)
    job.transtype(0).params shouldBe Map("a" -> "A", "b" -> "C", "d" -> "D")
  }

  // Queue

  "Queue" should "add new job" in {
    //    withDatabase { implicit connection =>
    //      val queue = app.injector.instanceOf[Queue]
    //      val create = Create(IN_URL, OUT_URL, List("html5", "upload"), None, Map.empty)
    //      queue.add(create)
    //
    //      val jobRes = map("SELECT count(ID) FROM job",
    //        res => 1,
    //        res => res.getLong(1))
    //      jobRes(1) shouldBe 5
    //
    //      val taskRes = map("SELECT count(ID) FROM task",
    //        res => 1,
    //        res => res.getLong(1))
    //      taskRes(1) shouldBe 10
    //    }
  }

  "Queue" should "list contents" in {
    //    withDatabase { implicit connection =>
    //      val queue = app.injector.instanceOf[Queue]
    //      val contents = queue.contents(Filter(None))
    //
    //      contents.size shouldBe 4
    //      contents(0).id shouldBe "a"
    //      contents(0).status shouldBe StatusString.Queue
    //      contents(1).id shouldBe "b"
    //      contents(1).status shouldBe StatusString.Process
    //      contents(2).id shouldBe "c"
    //      contents(2).status shouldBe StatusString.Process
    //      contents(3).id shouldBe "d"
    //      contents(3).status shouldBe StatusString.Error
    //    }
  }

  // Internal

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

  "Transtype alias" should "preserve unknown transtypes" in {
    queue.getTranstypes(List("foo", "bar")) shouldBe List("foo", "bar")
  }

  it should "subsitute alias in list" in {
    queue.getTranstypes(List("foo", "duo", "bar")) shouldBe List("foo", "first", "second", "bar")
  }

  it should "subsitute alias as only entry" in {
    queue.getTranstypes(List("duo")) shouldBe List("first", "second")
  }

  "Transtype params" should "preserve unknown transtypes" in {
    queue.getParams(List("foo", "bar")) shouldBe Map.empty
  }

  it should "subsitute alias in list" in {
    queue.getParams(List("foo", "duo", "single", "bar")) shouldBe Map("foo" -> "bar", "bar" -> "baz")
  }

  it should "subsitute alias as only entry" in {
    queue.getParams(List("duo")) shouldBe Map("foo" -> "bar")
  }

  private def insertJob(job: Job): Unit = {
    queue.data += job.id -> job
  }
}

