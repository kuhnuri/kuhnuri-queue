package services

import java.net.URI
import java.sql.{Connection, ResultSet}
import java.time._
import java.util.concurrent.atomic.AtomicInteger

import generated.Tables._
import generated.enums.Status
import models.request.{Create, Filter, JobResult}
import models.{Job, StatusString, Task, Worker}
import org.jooq.impl.DSL
import org.jooq.{Query, SQLDialect}
import org.scalatest._
import play.api.db.{Database, Databases}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Configuration, Mode}

import scala.collection.mutable
import scala.io.Source

class DBQueueSpec extends FlatSpec with Matchers with BeforeAndAfter with BeforeAndAfterEach {

  private val IN_URL = "file://in"
  private val OUT_URL = "file://out"
  private val JOB_A = "id-A"
  private val TASK_A = "id-A_1"
  private val TASK_B = "id-A_2"
  private val WORKER_ID = "worker-id"

  private val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC.normalized())
  private val now = LocalDateTime.now(clock) //.atOffset(ZoneOffset.UTC)

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

  private val app = new GuiceApplicationBuilder()
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
        //            connection.createStatement().execute(fixture)
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
              DELETE FROM job; DELETE FROM task;
              """)
          }

        }
  }

  // Dispatcher

  "Empty queue" should "return nothing" in {
    val queue = app.injector.instanceOf[Dispatcher]
    queue.request(List("unsupported"), worker) match {
      case Some(res) => fail
      case None =>
    }
  }

  "Queue with finished items" should "return nothing" in {
    withDatabase { implicit connection =>
      insertJob(Job(JOB_A, IN_URL, OUT_URL,
        List(
          Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5", Map.empty,
            StatusString.Done, Some(now.minusMinutes(10)), Some(WORKER_ID), Some(now.minusMinutes(5)))
        ),
        0, now.minusHours(1), Some(now.minusMinutes(5)), StatusString.Done)
      )

      val queue = app.injector.instanceOf[Dispatcher]
      queue.request(List("html5"), worker) match {
        case Some(res) => fail
        case None =>
      }
    }
  }

  "Job with single task" should "return first task" in {
    withDatabase { implicit connection =>
      insertJob(
        Job(JOB_A, IN_URL, OUT_URL,
          List(
            Task(TASK_A, JOB_A, None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
          ),
          0, now.minusHours(1), None, StatusString.Queue)
      )

      val queue = app.injector.instanceOf[Dispatcher]
      queue.request(List("html5"), worker) match {
        case Some(res) => {
          res.transtype shouldBe "html5"
          res.id shouldBe TASK_A
          res.input shouldBe Some(IN_URL)
          res.output shouldBe Some(OUT_URL)
          res.worker shouldBe Some(WORKER_ID)
          //          res.processing shouldBe Some(now))
          res.status shouldBe StatusString.Process
        }
        case None => fail
      }
    }
  }

  "Job with two tasks" should "return first task" in {
    withDatabase { implicit connection =>
      insertJob(Job(JOB_A, IN_URL, OUT_URL,
        List(
          Task(TASK_A, JOB_A, None, None, "graphics", Map.empty, StatusString.Queue, None, None, None),
          Task(TASK_B, JOB_A, None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, now.minusHours(1), None, StatusString.Queue)
      )

      val queue = app.injector.instanceOf[Dispatcher]
      queue.request(List("graphics"), worker) match {
        case Some(res) => {
          res.transtype shouldBe "graphics"
          res.id shouldBe TASK_A
          res.input shouldBe Some(IN_URL)
          res.output shouldBe None
          res.worker shouldBe Some(WORKER_ID)
          //          res.processing shouldBe Some(now)
          res.status shouldBe StatusString.Process
        }
        case None => fail
      }
    }
  }

  "Job with one successful task" should "return second task" in {
    withDatabase { implicit connection =>
      insertJob(Job(JOB_A, IN_URL, OUT_URL,
        List(
          Task(TASK_A, JOB_A, Some(IN_URL), Some("file:/dst/userguide.zip"),
            "html5", Map.empty, StatusString.Done, Some(now.minusMinutes(10)),
            Some(WORKER_ID), Some(now.minusMinutes(1))),
          Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, now.minusHours(1), None, StatusString.Process)
      )

      val queue = app.injector.instanceOf[Dispatcher]
      queue.request(List("html5", "upload"), worker) match {
        case Some(res) => {
          res.transtype shouldBe "upload"
          res.id shouldBe TASK_B
          res.input shouldBe Some("file:/dst/userguide.zip")
          res.output shouldBe Some(OUT_URL)
          res.worker shouldBe Some(WORKER_ID)
          //          res.processing shouldBe Some(now)
          res.status shouldBe StatusString.Process
        }
        case None => fail
      }
    }
  }

  "Job with one active task" should "not return second task" in {
    withDatabase { implicit connection =>
      insertJob(Job(JOB_A, IN_URL, OUT_URL,
        List(
          Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5", Map.empty,
            StatusString.Process, Some(now.minusMinutes(10)), Some(WORKER_ID), None),
          Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, now.minusHours(1), None, StatusString.Process)
      )

      val queue = app.injector.instanceOf[Dispatcher]
      queue.request(List("upload"), worker) match {
        case Some(_) => fail
        case None =>
      }
    }
  }

  "Job with single active task" should "accept job with update output" in {
    withDatabase { implicit connection =>
      val queue = app.injector.instanceOf[Dispatcher]
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

      //          val job = queue.data(JOB_A)
      //          job.output shouldBe "file:/dst/userguide.zip"
      //          job.transtype.head.status shouldBe StatusString.Done
      //          job.transtype.head.output shouldBe Some("file:/dst/userguide.zip")
      //          job.finished shouldBe Some(now)
      //          job.status shouldBe StatusString.Done
    }
  }

  "Job with first active task" should "accept job with update output" in {
    withDatabase { implicit connection =>

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

      val queue = app.injector.instanceOf[Dispatcher]
      queue.submit(JobResult(res, List.empty))

      //    val job = queue.data(JOB_A)
      //    job.output shouldBe OUT_URL
      //    job.transtype(0).status shouldBe StatusString.Done
      //    job.transtype(0).input shouldBe Some(IN_URL)
      //    job.transtype(0).output shouldBe Some("file:/tmp/userguide.zip")
      //    job.transtype(1).status shouldBe StatusString.Queue
      //    job.transtype(1).input shouldBe None
      //    job.transtype(1).output shouldBe None
      //    job.finished shouldBe None
      //    job.status shouldBe StatusString.Process
    }
  }

  "Job with second active task" should "accept job with update output" in {
    withDatabase { implicit connection =>

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

      val queue = app.injector.instanceOf[Dispatcher]
      queue.submit(JobResult(res, List.empty))

      //    val job = queue.data(JOB_A)
      //    job.output shouldBe OUT_URL
      //    job.transtype(0).status shouldBe StatusString.Done
      //    job.transtype(0).output shouldBe Some("file:/dst/userguide.zip")
      //    job.transtype(1).status shouldBe StatusString.Done
      //    job.transtype(1).input shouldBe Some("file:/dst/userguide.zip")
      //    job.transtype(1).output shouldBe Some(OUT_URL)
      //    job.finished shouldBe Some(now)
      //    job.status shouldBe StatusString.Done
    }
  }

  "Job with last active task" should "accept job with update output" in {
    withDatabase { implicit connection =>
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

      val queue = app.injector.instanceOf[Dispatcher]
      queue.submit(JobResult(res, List.empty))

      //    val job = queue.data(JOB_A)
      //    job.output shouldBe OUT_URL
      //    job.transtype(0).status shouldBe StatusString.Process
      //    job.transtype(0).output shouldBe Some("file:/dst/userguide.zip")
      //    job.transtype(1).status shouldBe StatusString.Queue
      //    job.transtype(1).input shouldBe None
      //    job.transtype(1).output shouldBe None
      //    job.finished shouldBe None
      //    job.status shouldBe StatusString.Process
    }
  }

  "Submit with new task parameters" should "be added to work parameters" in {
    withDatabase { implicit connection =>
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

      val queue = app.injector.instanceOf[Dispatcher]
      queue.submit(JobResult(res, List.empty))
      //
      //    val job = queue.data(JOB_A)
      //    job.transtype(0).params shouldBe Map("a" -> "A", "b" -> "C", "d" -> "D")
    }
  }


  "Queue" should "offer first job" in {
    withDatabase { implicit connection =>
      connection.createStatement().execute(fixture)

      val dispatcher = app.injector.instanceOf[Dispatcher]
      dispatcher.request(List("html5"), worker)

      val taskRes = map("SELECT id, status FROM task",
        res => res.getInt(1),
        res => res.getString(2))

      taskRes shouldBe Map(
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
  }

  "Queue" should "offer second job" in {
    withDatabase { implicit connection =>
      connection.createStatement().execute(fixture)

      val dispatcher = app.injector.instanceOf[Dispatcher]
      dispatcher.request(List("html5", "upload"), worker)
      dispatcher.request(List("html5", "upload"), worker)

      val taskRes = map("SELECT id, status FROM task",
        res => res.getInt(1),
        res => res.getString(2))

      taskRes shouldBe Map(
        1 -> "process",
        2 -> "queue",
        3 -> "process",
        4 -> "queue",
        5 -> "done",
        6 -> "process",
        7 -> "done",
        8 -> "error",
      )
    }
  }

  "Queue" should "offer second task of first job" in {
    withDatabase { implicit connection =>
      connection.createStatement().execute(fixture)

      val dispatcher = app.injector.instanceOf[Dispatcher]
      val task = dispatcher.request(List("html5", "upload"), worker).get
      val taskResult = JobResult(task.copy(status = StatusString.Done, output = Some("file://tmp")), List.empty)
      dispatcher.submit(taskResult)
      dispatcher.request(List("html5", "upload"), worker)

      val taskRes = map("SELECT id, status FROM task",
        res => res.getInt(1),
        res => res.getString(2))

      taskRes shouldBe Map(
        1 -> "done",
        2 -> "process",
        3 -> "process",
        4 -> "queue",
        5 -> "done",
        6 -> "queue",
        7 -> "done",
        8 -> "error",
      )
    }
  }

  // Queue

  "Queue" should "add new job" in {
    withDatabase { implicit connection =>
      connection.createStatement().execute(fixture)

      val queue = app.injector.instanceOf[Queue]
      val create = Create(None, IN_URL, OUT_URL, List("html5", "upload"), None, Map.empty)
      queue.add(create)

      val jobRes = map("SELECT count(ID) FROM job",
        res => 1,
        res => res.getLong(1))
      jobRes(1) shouldBe 5

      val taskRes = map("SELECT count(ID) FROM task",
        res => 1,
        res => res.getLong(1))
      taskRes(1) shouldBe 10
    }
  }

  "Queue" should "list contents" in {
    withDatabase { implicit connection =>
      connection.createStatement().execute(fixture)

      val queue = app.injector.instanceOf[Queue]
      val contents = queue.contents(Filter(None))

      contents.size shouldBe 4
      contents(0).id shouldBe "a"
      contents(0).status shouldBe StatusString.Queue
      contents(1).id shouldBe "b"
      contents(1).status shouldBe StatusString.Process
      contents(2).id shouldBe "c"
      contents(2).status shouldBe StatusString.Process
      contents(3).id shouldBe "d"
      contents(3).status shouldBe StatusString.Error
    }
  }

  //    "return nothing for finished items" in withDatabase { implicit connection =>
  //      insert("""
  //        """)
  //      insertJob(Job(JOB_A, IN_URL, OUT_URL,
  //        List(
  //          Task(TASK_A, JOB_A, Some(IN_URL), Some(OUT_URL), "html5", Map.empty,
  //            StatusString.Done, Some(now.minusMinutes(10)), Some(WORKER_ID), Some(now.minusMinutes(5)))
  //        ),
  //        0, now.minusHours(1), Some(now.minusMinutes(5)), StatusString.Done)
  //
  //      queue.request(List("html5"), worker) match {
  //        case Some(res) => fail
  //        case None =>
  //      }
  //    }


  //  "Job with single task" should "return first task" in {
  //    insertJob(Job(JOB_A,
  //      IN_URL,
  //      OUT_URL,
  //      List(
  //        Task(TASK_A, JOB_A, None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, now.minusHours(1), None)
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
  //    insertJob(Job(JOB_A,
  //      IN_URL,
  //      OUT_URL,
  //      List(
  //        Task(TASK_A, JOB_A, Some( IN_URL),
  //          Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Done,
  //          Some(now.minusMinutes(10)), Some(WORKER_ID), Some(now.minusMinutes(1))),
  //        Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, now.minusHours(1), None)
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
  //    insertJob(Job(JOB_A,
  //      IN_URL,
  //      OUT_URL,
  //      List(
  //        Task(TASK_A, JOB_A, Some(IN_URL),
  //          Some(OUT_URL), "html5", Map.empty, StatusString.Process,
  //          Some(now), Some(WORKER_ID), None)
  //      ),
  //      0, now.minusHours(1), None)
  //
  //    val res = Task(TASK_A, JOB_A, Some(IN_URL),
  //      Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Done,
  //      Some(now), Some(WORKER_ID), None)
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
  //    insertJob(Job(JOB_A,
  //      IN_URL,
  //      OUT_URL,
  //      List(
  //        Task(TASK_A, JOB_A, Some(IN_URL),
  //          Some(OUT_URL), "html5", Map.empty, StatusString.Process,
  //          Some(now), Some(WORKER_ID), None),
  //        Task(TASK_B, JOB_A, None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
  //      ),
  //      0, now.minusHours(1), None)
  //
  //    val res = Task(TASK_A, JOB_A, Some(IN_URL),
  //      Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Process,
  //      Some(now), Some(WORKER_ID), None)
  //    queue.submit(JobResult(res, List.empty))
  //
  //    val job = queue.data(JOB_A)
  //    job.output shouldBe OUT_URL
  //    job.transtype.head.status shouldBe StatusString.Process
  //    job.transtype.head.output shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
  //    job.finished.isDefined shouldBe false
  //  }

  private def withDatabase(block: (Connection) => Unit): Unit = {
    val connection = database.getConnection()
    try {
      block(connection)
    } finally {
      connection.close()
    }
  }

  private def list[T](query: String, map: ResultSet => T)
                     (implicit connection: Connection): Seq[T] = {
    val taskRes = connection.prepareStatement(query).executeQuery()
    val buf = mutable.Buffer[T]()
    while (taskRes.next()) {
      buf += map(taskRes)
    }
    buf.toList
  }

  private def map[K, T](query: String, key: ResultSet => K, value: ResultSet => T)
                       (implicit connection: Connection): Map[K, T] = {
    val taskRes = connection.prepareStatement(query).executeQuery()
    val buf = mutable.Buffer[(K, T)]()
    while (taskRes.next()) {
      buf += key(taskRes) -> value(taskRes)
    }
    buf.toMap
  }

  private def insertJob(job: Job)(implicit connection: Connection): Unit = {
    val jobId: AtomicInteger = new AtomicInteger(0)
    val taskId: AtomicInteger = new AtomicInteger(0)
    val sql = DSL.using(connection, SQLDialect.POSTGRES_9_4)
    val tasks: Seq[(Task, Int)] = job.transtype.zipWithIndex
    val value: List[Query] = List(
      sql
        .insertInto(JOB, JOB.ID, JOB.UUID, JOB.CREATED, JOB.INPUT, JOB.OUTPUT, JOB.FINISHED, JOB.PRIORITY)
        .values(jobId.incrementAndGet(), job.id,
          if (job.created != null) job.created else now,
          job.input, job.output,
          job.finished.map(localDateTimeToOffsetDateTime).getOrElse(null),
          job.priority)
    ) ++ tasks.map { case (task: Task, i: Int) =>
      sql
        .insertInto(TASK, TASK.ID, TASK.UUID, TASK.JOB, TASK.TRANSTYPE, TASK.INPUT, TASK.OUTPUT, TASK.STATUS,
          TASK.PROCESSING, TASK.FINISHED, TASK.WORKER, TASK.POSITION)
        .values(
          taskId.incrementAndGet(), task.id, jobId.get(), task.transtype, task.input.orNull, task.output.orNull,
          Status.valueOf(task.status.toString), task.processing.map(localDateTimeToOffsetDateTime).orNull,
          task.finished.map(localDateTimeToOffsetDateTime).orNull,
          task.worker.orNull, i + 1
        )
    }
    System.err.println(value)
    val ints: Array[Int] = sql
      .batch(
        value: _*)
      .execute()
    System.err.println(ints.toList)
    ()
  }

  private implicit def localDateTimeToOffsetDateTime(localDateTime: LocalDateTime): OffsetDateTime =
    OffsetDateTime.of(localDateTime, ZoneOffset.UTC)

}

