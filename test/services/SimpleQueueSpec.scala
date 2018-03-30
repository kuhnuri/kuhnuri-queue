package services

import java.net.URI
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import models.request.JobResult
import models.{Job, StatusString, Task, Worker}
import org.scalatest._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
class SimpleQueueSpec extends FlatSpec with Matchers with BeforeAndAfter {

  private val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC.normalized())
  private val now = LocalDateTime.now(clock).atOffset(ZoneOffset.UTC)

  private val app = new GuiceApplicationBuilder()
    .configure(Map(
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
      case Some(res) => fail
      case None =>
    }
  }

  "Job with single task" should "return first task" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out/",
      List(
        Task("id-A_1", "id-A", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None)

    queue.request(List("html5"), worker) match {
      case Some(res) => {
        res.transtype shouldBe "html5"
        res.id shouldBe "id-A_1"
        res.input shouldBe Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap")
        res.output shouldBe Some("file:/Volumes/tmp/out/")
        res.worker shouldBe Some("worker-id")
      }
      case None => fail
    }
  }

  "Job with one successful task" should "return second task" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out/",
      List(
        Task("id-A_1", "id-A", Some( "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Done,
          Some(queue.now.minusMinutes(10)), Some("worker-id"), Some(queue.now.minusMinutes(1))),
        Task("id-A_2", "id-A", None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None)

    queue.request(List("upload"), worker) match {
      case Some(res) => {
        res.transtype shouldBe "upload"
        res.id shouldBe "id-A_2"
        res.input shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
        res.output shouldBe Some("file:/Volumes/tmp/out/")
        res.worker shouldBe Some("worker-id")
      }
      case None => fail
    }
  }

  "Job with single active task" should "accept job with update output" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out/",
      List(
        Task("id-A_1", "id-A", Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          Some("file:/Volumes/tmp/out/"), "html5", Map.empty, StatusString.Process,
          Some(queue.now), Some("worker-id"), None)
      ),
      0, queue.now.minusHours(1), None)

    val res = Task("id-A_1", "id-A", Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
      Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Done,
      Some(queue.now), Some("worker-id"), None)
    queue.submit(JobResult(res, List.empty))

    val job = queue.data("id-A")
    job.output shouldBe "file:/Volumes/tmp/out/userguide.zip"
    job.transtype.head.status shouldBe StatusString.Done
    job.transtype.head.output shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
    job.finished.isDefined shouldBe true
  }


  "Job with one active task" should "accept job with update output" in {
    queue.data += "id-A" -> Job("id-A",
      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
      "file:/Volumes/tmp/out/",
      List(
        Task("id-A_1", "id-A", Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          Some("file:/Volumes/tmp/out/"), "html5", Map.empty, StatusString.Process,
          Some(queue.now), Some("worker-id"), None),
        Task("id-A_2", "id-A", None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
      ),
      0, queue.now.minusHours(1), None)

    val res = Task("id-A_1", "id-A", Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
      Some("file:/Volumes/tmp/out/userguide.zip"), "html5", Map.empty, StatusString.Process,
      Some(queue.now), Some("worker-id"), None)
    queue.submit(JobResult(res, List.empty))

    val job = queue.data("id-A")
    job.output shouldBe "file:/Volumes/tmp/out/"
    job.transtype.head.status shouldBe StatusString.Process
    job.transtype.head.output shouldBe Some("file:/Volumes/tmp/out/userguide.zip")
    job.finished.isDefined shouldBe false
  }

//  "SimpleQueue" should "return second job" in {
//    queue.data += "id-B" -> Job("id-B",
//      "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
//      "file:/Volumes/tmp/out/",
//      List(
//        Task("id-B_1", "id-B", None, None, "html5", Map.empty, StatusString.Queue, None, None, None),
//        Task("id-B_2", "id-B", None, None, "upload", Map.empty, StatusString.Queue, None, None, None)
//      ),
//      0, queue.now.minusHours(2), None)
//
//    queue.request(List("html5"), worker) match {
//      case Some(res) => {
//        res.transtype shouldBe "html5"
//        res.id shouldBe "id-B_1"
//        res.input shouldBe Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap")
//        res.output shouldBe Some("file:/Volumes/tmp/out/")
//        res.worker shouldBe Some("worker-id")
//
//        queue.submit(JobResult(res, List.empty))
//
//        val job = queue.data("id-B")
//        job.finished.isDefined shouldBe true
//      }
//      case None => fail
//    }
//  }
  //
  //  "SimpleQueue" should "return first task" in {
  //    queue.request(List("upload"), worker) match {
  //      case Some(res) => {
  //        res.transtype shouldBe "upload"
  //        res.id shouldBe "id-B_2"
  //        res.input shouldBe Some("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap")
  //        res.output shouldBe Some("file:/Volumes/tmp/out/")
  //        res.worker shouldBe Some("worker-id")
  //
  //        queue.submit(JobResult(res, List.empty))
  //
  //        val job = queue.data("id-B")
  //        job.finished.isDefined shouldBe true
  //      }
  //      case None => fail
  //    }
  //  }


  //
  //    var token: String = null
  //
  //    "accept registration" in {
  //      val query = JsObject(Map(
  //        "id" -> JsString("worker"),
  //        "password" -> JsString("password"),
  //        "uri" -> JsString("http://example.com/")
  //      ))
  //      val registration = route(app, FakeRequest(POST, "/api/v1/login")
  //        .withJsonBody(query)
  //      ).get
  //
  //      status(registration) mustBe OK
  //      token = headers(registration).apply(AUTH_TOKEN_HEADER)
  //    }
  //
  //    "return job" in {
  //      val query = JsArray(List(
  //        JsString("html5")
  //      ))
  //      val first = route(app, FakeRequest(POST, "/api/v1/work")
  //        .withJsonBody(query)
  //        .withHeaders(AUTH_TOKEN_HEADER -> token)
  //      ).get
  //
  //      status(first) mustBe OK
  //      contentType(first) mustBe Some("application/json")
  //      contentAsJson(first) mustEqual JsObject(Map(
  //        "created" -> JsString(now.minusHours(2).toString),
  //        //        "finished" -> JsNull,
  //        "id" -> JsString("id-A1"),
  //        "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //        "output" -> JsString("file:/Volumes/tmp/out/"),
  //        "transtype" -> JsString("html5"),
  //        "params" -> JsObject(List.empty),
  //        "priority" -> JsNumber(0),
  //        "processing" -> JsString(now.toString),
  //        "worker" -> JsString("worker"),
  //        "status" -> JsString("process")
  //      ))
  //    }
  //
  //    "return job again" in {
  //      val query = JsArray(List(
  //        JsString("html5")
  //      ))
  //      val second = route(app, FakeRequest(POST, "/api/v1/work")
  //        .withJsonBody(query)
  //        .withHeaders(AUTH_TOKEN_HEADER -> token)
  //      ).get
  //
  //      status(second) mustBe OK
  //      contentType(second) mustBe Some("application/json")
  //      contentAsJson(second) mustEqual JsObject(Map(
  //        "created" -> JsString(now.minusHours(2).toString),
  //        //        "finished" -> JsNull,
  //        "id" -> JsString("id-A1"),
  //        "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //        "output" -> JsString("file:/Volumes/tmp/out/"),
  //        "transtype" -> JsString("html5"),
  //        "params" -> JsObject(List.empty),
  //        "priority" -> JsNumber(0),
  //        "processing" -> JsString(now.toString),
  //        "worker" -> JsString("worker"),
  //        "status" -> JsString("process")
  //      ))
  //
  //      route(app, FakeRequest(POST, "/api/v1/work")
  //        .withJsonBody(query)
  //        .withHeaders(AUTH_TOKEN_HEADER -> token)
  //      ).map(status) mustBe Some(OK) // This should be NO_CONTENT
  //    }
  //
  //    "return PDF job" in {
  //      val query = JsArray(List(
  //        JsString("pdf")
  //      ))
  //      val home = route(app, FakeRequest(POST, "/api/v1/work")
  //        .withJsonBody(query)
  //        .withHeaders(AUTH_TOKEN_HEADER -> token)
  //      ).get
  //
  //      status(home) mustBe OK
  //      contentType(home) mustBe Some("application/json")
  //      contentAsJson(home) mustEqual JsObject(Map(
  //        "created" -> JsString(now.toString),
  //        //        "finished" -> JsNull,
  //        "id" -> JsString("id-B"),
  //        "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //        "output" -> JsString("file:/Volumes/tmp/out/"),
  //        "transtype" -> JsString("pdf"),
  //        "params" -> JsObject(List.empty),
  //        "priority" -> JsNumber(0),
  //        "processing" -> JsString(now.toString),
  //        "worker" -> JsString("worker"),
  //        "status" -> JsString("process")
  //      ))
  //    }
  //
  //    "return XHTML job" in {
  //      val query = JsArray(List(
  //        JsString("xhtml")
  //      ))
  //
  //      val home = route(app, FakeRequest(POST, "/api/v1/work")
  //        .withJsonBody(query)
  //        .withHeaders(AUTH_TOKEN_HEADER -> token)
  //      ).get
  //
  //      val text = contentAsString(home)
  //
  //      status(home) mustBe NO_CONTENT
  //    }
  //  }

  //  "ListController" should {
  //
  //    "list jobs" in {
  //      val home = route(app, FakeRequest(GET, "/api/v1/jobs")).get
  //
  //      status(home) mustBe OK
  //      contentType(home) mustBe Some("application/json")
  //      sortById(contentAsJson(home)) mustEqual JsArray(Seq(
  //        JsObject(Map(
  //          "id" -> JsString("id-A"),
  //          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //          "output" -> JsString("file:/Volumes/tmp/out"),
  //          "transtype" -> JsString("html5"),
  //          "params" -> JsObject(List.empty)
  //        )),
  //        JsObject(Map(
  //          "id" -> JsString("id-B"),
  //          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //          "output" -> JsString("file:/Volumes/tmp/out"),
  //          "transtype" -> JsString("pdf"),
  //          "params" -> JsObject(List.empty)
  //        )
  //        )))
  //    }
  //
  //    "show job details" in {
  //      val home = route(app, FakeRequest(GET, "/api/v1/job/id-B")).get
  //
  //      status(home) mustBe OK
  //      contentType(home) mustBe Some("application/json")
  //      contentAsJson(home) mustEqual JsObject(Map(
  //        "id" -> JsString("id-B"),
  //        "output" -> JsString("file:/Volumes/tmp/out"),
  //        "status" -> JsString(Queue.toString)
  //      ))
  //    }
  //
  //    "send 404 on a missing job" in {
  //      route(app, FakeRequest(GET, "/api/v1/job/X")).map(status(_)) mustBe Some(NOT_FOUND)
  //    }
  //
  //    "add new job" should {
  //
  //      "add" in {
  //        val body = JsObject(Map(
  //          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //          "output" -> JsString("file:/Volumes/tmp/out"),
  //          "transtype" -> JsString("html5"),
  //          "params" -> JsObject(List.empty)
  //        ))
  //        val created = route(app, FakeRequest(POST, "/api/v1/job").withJsonBody(body)).get
  //
  //        status(created) mustBe CREATED
  //        contentType(created) mustBe Some("application/json")
  //        //        contentAsJson(created) mustEqual JsObject(Map(
  //        //            "id" -> JsString("id-A"),
  //        //            "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
  //        //            "output" -> JsString("file:/Volumes/tmp/out"),
  //        //            "transtype" -> JsString("html5"),
  //        //            "params" -> JsObject(List.empty)
  //        //          ))
  //
  //        val res = route(app, FakeRequest(GET, "/api/v1/jobs")).map(contentAsJson(_)).get.as[JsArray]
  //        res.value.size mustBe 3
  //      }
  //    }
  //  }

}

