package services

import java.net.URI
import java.sql.{Connection, ResultSet}
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import models.request.{Create, Filter, JobResult}
import models.{StatusString, Worker}
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

  private val IN_URL = "file://in"
  private val OUT_URL = "file://out"
  private val JOB_A = "id-A"
  private val TASK_A = "id-A_1"
  private val TASK_B = "id-A_2"
  private val WORKER_ID = "worker-id"

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

  "Queue" should {
    "add new job" in withDatabase { implicit connection =>
      val queue = app.injector.instanceOf[Queue]
      val create = Create(IN_URL, OUT_URL, List("html5", "upload"), None, Map.empty)
      queue.add(create)

      val jobRes = map("SELECT count(ID) FROM job",
        res => 1,
        res => res.getLong(1))
      jobRes(1) mustBe 5

      val taskRes = map("SELECT count(ID) FROM task",
        res => 1,
        res => res.getLong(1))
      taskRes(1) mustBe 10
    }

    "list contents" in withDatabase { implicit connection =>
      val queue = app.injector.instanceOf[Queue]
      val contents = queue.contents(Filter(None))

      contents.size mustBe 4
      contents(0).id mustBe "a"
      contents(0).status mustBe StatusString.Queue
      contents(1).id mustBe "b"
      contents(1).status mustBe StatusString.Process
      contents(2).id mustBe "c"
      contents(2).status mustBe StatusString.Process
      contents(3).id mustBe "d"
      contents(3).status mustBe StatusString.Error
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

  private def insert(query: String)(implicit connection: Connection): Unit = {
    connection.createStatement().execute(query)
  }

  "Dispatcher" should {
    "Empty queue should return nothing" in {
      val queue = app.injector.instanceOf[Dispatcher]
      queue.request(List("unsupported"), worker) match {
        case Some(res) => fail
        case None =>
      }
    }

    //    "return nothing for finished items" in withDatabase { implicit connection =>
    //      insert("""
    //        """)
    //      queue.data += JOB_A -> Job(JOB_A, IN_URL, OUT_URL,
    //        List(
    //          Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5", Map.empty,
    //            StatusString.Done, Some(queue.now.minusMinutes(10)), Some(WORKER_ID), Some(queue.now.minusMinutes(5)))
    //        ),
    //        0, queue.now.minusHours(1), Some(queue.now.minusMinutes(5)), StatusString.Done)
    //
    //      queue.request(List("html5"), worker) match {
    //        case Some(res) => fail
    //        case None =>
    //      }
    //    }

    "offer first job" in withDatabase { implicit connection =>
      val dispatcher = app.injector.instanceOf[Dispatcher]
      dispatcher.request(List("html5"), worker)

      val taskRes = map("SELECT id, status FROM task",
        res => res.getInt(1),
        res => res.getString(2))

      taskRes mustBe Map(
        1 -> "process",
        2 -> "queue",
        3 -> "process",
        4 -> "queue",
        5 -> "done",
        6 -> "queue",
        7 -> "done",
        8 -> "error",
      )
    }

    "offer second job" in withDatabase { implicit connection =>
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

    "offer second task of first job" in withDatabase { implicit connection =>
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
      taskRes(3) mustBe "process"
      taskRes(4) mustBe "queue"
    }
  }

  //  "Job with single task" should "return first task" in {
  //    queue.data += JOB_A -> Job(JOB_A,
  //      IN_URL,
  //      OUT_URL,
  //      List(
  //        Task(TASK_A, JOB_A, None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, queue.now.minusHours(1), None)
  //
  //    queue.request(List("html5"), worker) match {
  //      case Some(res) => {
  //        res.transtype shouldBe "html5"
  //        res.id shouldBe TASK_A
  //        res.input shouldBe Some(IN_URL)
  //        res.output shouldBe Some(OUT_URL)
  //        res.worker shouldBe Some(WORKER_ID)
  //      }
  //      case None => fail
  //    }
  //  }
  //
  //  "Job with one successful task" should "return second task" in {
  //    queue.data += JOB_A -> Job(JOB_A,
  //      IN_URL,
  //      OUT_URL,
  //      List(
  //        Task(TASK_A, JOB_A, Some( IN_URL),
  //          Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Done,
  //          Some(queue.now.minusMinutes(10)), Some(WORKER_ID), Some(queue.now.minusMinutes(1))),
  //        Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, queue.now.minusHours(1), None)
  //
  //    queue.request(List("upload"), worker) match {
  //      case Some(res) => {
  //        res.transtype shouldBe "upload"
  //        res.id shouldBe TASK_B
  //        res.input shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
  //        res.output shouldBe Some(OUT_URL)
  //        res.worker shouldBe Some(WORKER_ID)
  //      }
  //      case None => fail
  //    }
  //  }
  //
  //  "Job with single active task" should "accept job with update output" in {
  //    queue.data += JOB_A -> Job(JOB_A,
  //      IN_URL,
  //      OUT_URL,
  //      List(
  //        Task(TASK_A, JOB_A, Some(IN_URL),
  //          Some(OUT_URL), "html5", Map.empty, StatusString.Process,
  //          Some(queue.now), Some(WORKER_ID), None)
  //      ),
  //      0, queue.now.minusHours(1), None)
  //
  //    val res = Task(TASK_A, JOB_A, Some(IN_URL),
  //      Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Done,
  //      Some(queue.now), Some(WORKER_ID), None)
  //    queue.submit(JobResult(res, List.empty))
  //
  //    val job = queue.data(JOB_A)
  //    job.output shouldBe "file:/Volumes/tmp/out/userguide.zip"
  //    job.transtype.head.status shouldBe StatusString.Done
  //    job.transtype.head.output shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
  //    job.finished.isDefined shouldBe true
  //  }
  //
  //
  //  "Job with one active task" should "accept job with update output" in {
  //    queue.data += JOB_A -> Job(JOB_A,
  //      IN_URL,
  //      OUT_URL,
  //      List(
  //        Task(TASK_A, JOB_A, Some(IN_URL),
  //          Some(OUT_URL), "html5", Map.empty, StatusString.Process,
  //          Some(queue.now), Some(WORKER_ID), None),
  //        Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, queue.now.minusHours(1), None)
  //
  //    val res = Task(TASK_A, JOB_A, Some(IN_URL),
  //      Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Process,
  //      Some(queue.now), Some(WORKER_ID), None)
  //    queue.submit(JobResult(res, List.empty))
  //
  //    val job = queue.data(JOB_A)
  //    job.output shouldBe OUT_URL
  //    job.transtype.head.status shouldBe StatusString.Process
  //    job.transtype.head.output shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
  //    job.finished.isDefined shouldBe false
  //  }
}

