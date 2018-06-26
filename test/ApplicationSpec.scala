import java.time._

import models.{Job, StatusString, Task}
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.Helpers.{contentAsJson, contentType, _}
import play.api.test._
import services.{Dispatcher, DummyQueue, Queue}

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
class ApplicationSpec extends PlaySpec with GuiceOneAppPerSuite with BeforeAndAfter {

  private val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC.normalized())
  private val now = LocalDateTime.now(clock).atOffset(ZoneOffset.UTC)

  implicit override lazy val app = new GuiceApplicationBuilder()
    .configure(Map(
      "queue.timeout" -> "10m",
      "queue.users" -> List(Map(
        "username" -> "worker",
        "hash" -> "$2a$10$c.9YXZkSrElx2dz8udP8vOlZSfF/ftsf4EClIORt8ILWd8vciLING"
      )),
      "queue.temp" -> System.getProperty("java.io.tmpdir")
    ))
    .overrides(
      bind(classOf[Queue]).to(classOf[DummyQueue]),
      bind(classOf[Dispatcher]).to(classOf[DummyQueue]),
      bind(classOf[Clock]).to(clock)
    )
    //    .overrides(bind(classOf[Dispatcher]).to(classOf[DefaultDispatcher]))
    .build

  val queue = app.injector.instanceOf[Dispatcher].asInstanceOf[DummyQueue]

  after {
    queue.data.clear()
  }

  "Routes" should {

    "send 404 on a bad request" in {
      route(app, FakeRequest(GET, "/boum")).map(status(_)) mustBe Some(NOT_FOUND)
    }
  }

  def sortById(arr: JsValue): JsArray = {
    JsArray(arr.as[JsArray].value.toList.sortBy(v => (v \ "id").get.as[JsString].value))
  }

  "ListController" should {

    "list jobs" in {
      queue.data += "id-A" -> Job("id-A",
        "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
        "file:/Volumes/tmp/out/",
        List(
          Task("id-A_1", "id-A", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, queue.now.minusHours(1), None, StatusString.Queue)
      queue.data += "id-A1" -> Job("id-A1",
        "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
        "file:/Volumes/tmp/out/",
        List(
          Task("id-A1_1", "id-A1", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, queue.now.minusHours(2), None, StatusString.Queue)
      queue.data += "id-B" -> Job("id-B",
        "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
        "file:/Volumes/tmp/out/",
        List(
          Task("id-B_1", "id-B", None, None, "pdf", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, queue.now, None, StatusString.Queue)

      val home = route(app, FakeRequest(GET, "/api/v1/jobs")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("application/json")
      sortById(contentAsJson(home)) mustEqual JsArray(Seq(
        JsObject(Map(
          "id" -> JsString("id-A"),
          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          "output" -> JsString("file:/Volumes/tmp/out/"),
          "transtype" -> JsArray(List(JsObject(Map(
            "id" -> JsString("id-A_1"),
            "job" -> JsString("id-A"),
            "params" -> JsObject(List.empty),
            "status" -> JsString("queue"),
            "transtype" -> JsString("html5")
          )))),
          "status" -> JsString("queue"),
          "priority" -> JsNumber(0),
          "created" -> JsString(LocalDateTime.now(clock).minusHours(1).toString)
        )),
        JsObject(Map(
          "id" -> JsString("id-A1"),
          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          "output" -> JsString("file:/Volumes/tmp/out/"),
          "transtype" -> JsArray(List(JsObject(Map(
            "id" -> JsString("id-A1_1"),
            "job" -> JsString("id-A1"),
            "params" -> JsObject(List.empty),
            "status" -> JsString("queue"),
            "transtype" -> JsString("html5")
          )))),
          "status" -> JsString("queue"),
          "priority" -> JsNumber(0),
          "created" -> JsString(LocalDateTime.now(clock).minusHours(2).toString)
        )),
        JsObject(Map(
          "id" -> JsString("id-B"),
          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          "output" -> JsString("file:/Volumes/tmp/out/"),
          "transtype" -> JsArray(List(JsObject(Map(
            "id" -> JsString("id-B_1"),
            "job" -> JsString("id-B"),
            "params" -> JsObject(List.empty),
            "status" -> JsString("queue"),
            "transtype" -> JsString("pdf")
          )))),
          "status" -> JsString("queue"),
          "priority" -> JsNumber(0),
          "created" -> JsString(LocalDateTime.now(clock).toString)
        ))
      ))
    }

    "show job details" in {
      queue.data += "id-B" -> Job("id-B",
        "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
        "file:/Volumes/tmp/out/",
        List(
          Task("id-B_1", "id-B", None, None, "pdf", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, queue.now, None, StatusString.Queue)

      val home = route(app, FakeRequest(GET, "/api/v1/job/id-B")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("application/json")
      contentAsJson(home) mustEqual JsObject(Map(
        "id" -> JsString("id-B"),
        "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
        "output" -> JsString("file:/Volumes/tmp/out/"),
        "transtype" -> JsArray(List(JsObject(Map(
          "id" -> JsString("id-B_1"),
          "job" -> JsString("id-B"),
          "params" -> JsObject(List.empty),
          "status" -> JsString("queue"),
          "transtype" -> JsString("pdf")
        )))),
        "status" -> JsString("queue"),
        "priority" -> JsNumber(0),
        "created" -> JsString(LocalDateTime.now(clock).toString)
      ))
    }

    "send 404 on a missing job" in {
      route(app, FakeRequest(GET, "/api/v1/job/X")).map(status(_)) mustBe Some(NOT_FOUND)
    }

    "add new job" should {

      "add" in {
        val body = JsObject(Map(
          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          "output" -> JsString("file:/Volumes/tmp/out"),
          "transtype" -> JsArray(List(JsString("html5"))),
          "params" -> JsObject(List.empty)
        ))

        val created = route(app, FakeRequest(POST, "/api/v1/job").withJsonBody(body)).get

        status(created) mustBe CREATED
        contentType(created) mustBe Some("application/json")
        val data = contentAsJson(created)
        (data \ "input").get mustBe JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap")
        (data \ "output").get mustBe JsString("file:/Volumes/tmp/out")
        (data \ "status").get mustBe JsString("queue")
        (data \ "priority").get mustBe JsNumber(0)
        (data \ "created").get mustBe JsString(LocalDateTime.now(clock).toString)

        queue.data.size mustBe 1
      }
    }
  }

}
