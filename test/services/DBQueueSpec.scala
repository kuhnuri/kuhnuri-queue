package services

import java.net.URI
import java.sql.{Connection, ResultSet}
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import models.{StatusString, Worker}
import models.request.{Create, JobResult}
import org.scalatest.{BeforeAndAfterEach, TestData}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.db.{Database, Databases}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}

import scala.collection.mutable
import scala.io.Source

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
  val fixture = Source.fromInputStream(getClass.getResourceAsStream("/services/fixture.sql")).mkString

  override def beforeEach(): Unit = {
    try {
      super.beforeEach()
    } finally {
      withDatabase { connection =>
        connection.createStatement.execute(
          """
          DELETE FROM job;
          """)
        connection.createStatement().execute(fixture)
      }
    }
  }

  override def afterEach(): Unit = {
    try {
      super.afterEach()
    } finally {
      withDatabase { connection =>
        connection.createStatement.execute(
          """
          DELETE FROM job;
          """)
      }

    }
  }

  private def withDatabase(block: (Connection) => Unit): Unit = {
    val connection = database.getConnection()
    try {
      block(connection)
    } finally {
      connection.close()
    }
  }

  "Adding one" should {
    "insert into DB" in withDatabase { implicit connection =>
      val queue = app.injector.instanceOf[Queue]
      val create = Create(
        "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
        "file:/Volumes/tmp/out/", List("html5", "upload"), None, Map.empty
      )
      queue.add(create)

      val jobRes = map("SELECT count(ID) FROM job",
        res => 1,
        res => res.getLong(1))
      jobRes(1) mustBe 3

      val taskRes = map("SELECT count(ID) FROM task",
        res => 1,
        res => res.getLong(1))
      taskRes(1) mustBe 6
    }
  }

  private def list[T](query: String, map: ResultSet => T)(implicit connection: Connection): Seq[T] = {
    val taskRes = connection.prepareStatement(query).executeQuery()
    val buf = mutable.Buffer[T]()
    while (taskRes.next()) {
      buf += map(taskRes)
    }
    buf.toList
  }

  private def map[K, T](query: String, key: ResultSet => K, value: ResultSet => T)(implicit connection: Connection): Map[K, T] = {
    val taskRes = connection.prepareStatement(query).executeQuery()
    val buf = mutable.Buffer[(K, T)]()
    while (taskRes.next()) {
      buf += key(taskRes) -> value(taskRes)
    }
    buf.toMap
  }

  "Request one" should {
    "update and return" in withDatabase { implicit connection =>
      val dispatcher = app.injector.instanceOf[Dispatcher]
      dispatcher.request(List("html5"), worker)

      val taskRes = map("SELECT id, status FROM task",
        res => res.getInt(1),
        res => res.getString(2))

      taskRes(1) mustBe "process"
      taskRes(2) mustBe "queue"
      taskRes(3) mustBe "queue"
      taskRes(4) mustBe "queue"
    }

    "offer second task" in withDatabase { implicit connection =>
      val dispatcher = app.injector.instanceOf[Dispatcher]
      dispatcher.request(List("html5", "upload"), worker)
      dispatcher.request(List("html5", "upload"), worker)

      val taskRes = map("SELECT id, status FROM task",
        res => res.getInt(1),
        res => res.getString(2))

      taskRes(1) mustBe "process"
      taskRes(2) mustBe "queue"
      taskRes(3) mustBe "process"
      taskRes(4) mustBe "queue"
    }

    "finish first task" in withDatabase { implicit connection =>
      val dispatcher = app.injector.instanceOf[Dispatcher]
      val task = dispatcher.request(List("html5", "upload"), worker).get
      val taskResult = JobResult(task.copy(status = StatusString.Done), List.empty)
      dispatcher.submit(taskResult)
      dispatcher.request(List("html5", "upload"), worker)

      val taskRes = map("SELECT id, status FROM task",
        res => res.getInt(1),
        res => res.getString(2))

      taskRes(1) mustBe "done"
      taskRes(2) mustBe "process"
      taskRes(3) mustBe "queue"
      taskRes(4) mustBe "queue"
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

