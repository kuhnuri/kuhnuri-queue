package services

import java.net.URI
import java.sql.Connection
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import models.Worker
import models.request.Create
import org.scalatest.{BeforeAndAfterEach, TestData}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.db.{Database, Databases}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
class DBQueueSpec extends PlaySpec with GuiceOneAppPerTest with BeforeAndAfterEach {

  private val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC.normalized())
  private val now = LocalDateTime.now(clock).atOffset(ZoneOffset.UTC)

  //  val database = Databases.inMemory(
  //    name = "queue",
  //    urlOptions = Map(
  //      "MODE" -> "PostgreSQL"
  //    ),
  //    config = Map(
  //      "logStatements" -> true
  //    )
  //  )
  val database = Databases(
    driver = "org.postgresql.Driver",
    url = "jdbc:postgresql://localhost/queue?user=play",
    name = "queue"
  )

  implicit override def newAppForTest(testData: TestData): Application = new GuiceApplicationBuilder()
    .in(Mode.Test)
    .loadConfig(env => Configuration.load(env))
    .configure(Map(
      "queue.timeout" -> "10m",
      "queue.users" -> List(Map(
        "username" -> "worker",
        "hash" -> "$2a$10$c.9YXZkSrElx2dz8udP8vOlZSfF/ftsf4EClIORt8ILWd8vciLING"
      )),
    ))
    .overrides(
      bind(classOf[Database]).to(database),
      bind(classOf[Queue]).to(classOf[DBQueue]),
      bind(classOf[Dispatcher]).to(classOf[DBQueue]),
      bind(classOf[Clock]).to(clock)
    )
    .build()

  private val worker = Worker("token", "worker-id", URI.create("worker-uri"))

  override def beforeEach(): Unit = {
    try {
      super.beforeEach()
    } finally {
      withDatabase { connection =>
        connection.prepareStatement(
          """
        INSERT INTO job (id, uuid, created, input, output, finished, priority)
          VALUES (1, 'a', now(), 'file://in', 'file://out', null, 0);
        INSERT INTO task (id, uuid, transtype, input, output, status, processing, finished, worker, job, position)
          VALUES (1, 'a1', 'html5', null, null, 'queue', null, null, null, 1, 1),
                 (2, 'a2', 'upload', null, null, 'queue', null, null, null, 1, 2);
        """).execute()
      }
    }
  }

  override def afterEach(): Unit = {
    try {
      super.afterEach()
    } finally {
      withDatabase { connection =>
        connection.prepareStatement(
          """
          DELETE FROM job;
          """).execute()
      }

    }
  }

  def withDatabase(block: (Connection) => Unit): Unit = {
    val connection = database.getConnection()
    try {
      block(connection)
    } finally {
      connection.close()
    }
  }

  "Adding one" should {
    "insert into DB" in withDatabase { connection =>
      val queue = app.injector.instanceOf[Queue]
      val create = Create(
        "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
        "file:/Volumes/tmp/out/", List("html5", "upload"), None, Map.empty
      )
      queue.add(create)

      val jobRes = connection.prepareStatement(
        """
        SELECT count(ID) FROM job GROUP BY id
        """).executeQuery()
      jobRes.next()
      jobRes.getLong(1) mustBe 1

      val taskRes = connection.prepareStatement(
        """
        SELECT count(ID) FROM task GROUP BY job
        """).executeQuery()
      taskRes.next()
      taskRes.getLong(1) mustBe 2
    }
  }

  "Request one" should {
    "update and return" in withDatabase { connection =>
      val dispatcher = app.injector.instanceOf[Dispatcher]
      dispatcher.request(List("html5"), worker)

      val taskRes = connection.prepareStatement(
        """
        SELECT id, status FROM task ORDER BY id ASC
        """).executeQuery()
      taskRes.next()
      taskRes.getInt(1) mustBe 1
      taskRes.getString(2) mustBe "process"
      taskRes.next()
      taskRes.getInt(1) mustBe 2
      taskRes.getString(2) mustBe "queue"
    }
  }

  //  "Empty queue" should "return nothing" in {
  //    queue.request(List("unsupported"), worker) match {
  //      case Some(res) => fail
  //      case None =>
  //    }
  //  }
  //
  //  "Job with single task" should "return first task" in {
  //    queue.data += "id-A" -> Job("id-A",
  //      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
  //      "file:/Volumes/tmp/out/",
  //      List(
  //        Task("id-A_1", "id-A", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, queue.now.minusHours(1), None)
  //
  //    queue.request(List("html5"), worker) match {
  //      case Some(res) => {
  //        res.transtype shouldBe "html5"
  //        res.id shouldBe "id-A_1"
  //        res.input shouldBe Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap")
  //        res.output shouldBe Some("file:/Volumes/tmp/out/")
  //        res.worker shouldBe Some("worker-id")
  //      }
  //      case None => fail
  //    }
  //  }
  //
  //  "Job with one successful task" should "return second task" in {
  //    queue.data += "id-A" -> Job("id-A",
  //      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
  //      "file:/Volumes/tmp/out/",
  //      List(
  //        Task("id-A_1", "id-A", Some( "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //          Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Done,
  //          Some(queue.now.minusMinutes(10)), Some("worker-id"), Some(queue.now.minusMinutes(1))),
  //        Task("id-A_2", "id-A", None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, queue.now.minusHours(1), None)
  //
  //    queue.request(List("upload"), worker) match {
  //      case Some(res) => {
  //        res.transtype shouldBe "upload"
  //        res.id shouldBe "id-A_2"
  //        res.input shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
  //        res.output shouldBe Some("file:/Volumes/tmp/out/")
  //        res.worker shouldBe Some("worker-id")
  //      }
  //      case None => fail
  //    }
  //  }
  //
  //  "Job with single active task" should "accept job with update output" in {
  //    queue.data += "id-A" -> Job("id-A",
  //      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
  //      "file:/Volumes/tmp/out/",
  //      List(
  //        Task("id-A_1", "id-A", Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //          Some("file:/Volumes/tmp/out/"), "html5", Map.empty, StatusString.Process,
  //          Some(queue.now), Some("worker-id"), None)
  //      ),
  //      0, queue.now.minusHours(1), None)
  //
  //    val res = Task("id-A_1", "id-A", Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //      Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Done,
  //      Some(queue.now), Some("worker-id"), None)
  //    queue.submit(JobResult(res, List.empty))
  //
  //    val job = queue.data("id-A")
  //    job.output shouldBe "file:/Volumes/tmp/out/userguide.zip"
  //    job.transtype.head.status shouldBe StatusString.Done
  //    job.transtype.head.output shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
  //    job.finished.isDefined shouldBe true
  //  }
  //
  //
  //  "Job with one active task" should "accept job with update output" in {
  //    queue.data += "id-A" -> Job("id-A",
  //      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
  //      "file:/Volumes/tmp/out/",
  //      List(
  //        Task("id-A_1", "id-A", Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //          Some("file:/Volumes/tmp/out/"), "html5", Map.empty, StatusString.Process,
  //          Some(queue.now), Some("worker-id"), None),
  //        Task("id-A_2", "id-A", None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, queue.now.minusHours(1), None)
  //
  //    val res = Task("id-A_1", "id-A", Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //      Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Process,
  //      Some(queue.now), Some("worker-id"), None)
  //    queue.submit(JobResult(res, List.empty))
  //
  //    val job = queue.data("id-A")
  //    job.output shouldBe "file:/Volumes/tmp/out/"
  //    job.transtype.head.status shouldBe StatusString.Process
  //    job.transtype.head.output shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
  //    job.finished.isDefined shouldBe false
  //  }
}

