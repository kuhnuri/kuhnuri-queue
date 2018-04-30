import java.time.{Clock, Instant, LocalDateTime, ZoneOffset}

import filters.TokenAuthorizationFilter.AUTH_TOKEN_HEADER
import models.{Job, StatusString, Task}
import org.scalatest.{BeforeAndAfter, TestData}
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.Helpers.{contentAsJson, contentType, _}
import play.api.test._
import services.{Dispatcher, DummyQueue}

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
class WorkSpec extends PlaySpec with GuiceOneAppPerSuite with BeforeAndAfter {

  private val clock: Clock = Clock.fixed(Instant.now(), ZoneOffset.UTC.normalized())
  private val now = LocalDateTime.now(clock).atOffset(ZoneOffset.UTC)

  implicit override lazy val app = new GuiceApplicationBuilder()
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

  val queue = app.injector.instanceOf[Dispatcher].asInstanceOf[DummyQueue]

  after {
    queue.data.clear()
  }

  "WorkController" should {

    var token: String = null

    "accept registration" in {
      val query = JsObject(Map(
        "id" -> JsString("worker"),
        "password" -> JsString("password"),
        "uri" -> JsString("http://example.com/")
      ))
      val registration = route(app, FakeRequest(POST, "/api/v1/login")
        .withJsonBody(query)
      ).get

      status(registration) mustBe OK
      token = headers(registration).apply(AUTH_TOKEN_HEADER)
    }

    "return job" in {
      queue.data += "id-A" -> Job("id-A",
        "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
        "file:/Volumes/tmp/out/",
        List(
          Task("id-A_1", "id-A", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, queue.now.minusHours(1), None, StatusString.Queue)

      val query = JsArray(List(
        JsString("html5")
      ))
      val first = route(app, FakeRequest(POST, "/api/v1/work")
        .withJsonBody(query)
        .withHeaders(AUTH_TOKEN_HEADER -> token)
      ).get

      status(first) mustBe OK
      contentType(first) mustBe Some("application/json")
      contentAsJson(first) mustEqual JsObject(Map(
        "id" -> JsString("id-A_1"),
        "job" -> JsString("id-A"),
        "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
        "output" -> JsString("file:/Volumes/tmp/out/"),
        "transtype" -> JsString("html5"),
        "params" -> JsObject(List.empty),
        "processing" -> JsString(now.toString),
        "worker" -> JsString("worker"),
        "status" -> JsString("process")
      ))
    }

    "return job again" in {
      queue.data += "id-A" -> Job("id-A",
        "file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap",
        "file:/Volumes/tmp/out/",
        List(
          Task("id-A_1", "id-A", None, None, "html5", Map.empty, StatusString.Queue, None, None, None)
        ),
        0, queue.now.minusHours(1), None, StatusString.Queue)

      val query = JsArray(List(
        JsString("html5")
      ))
      val second = route(app, FakeRequest(POST, "/api/v1/work")
        .withJsonBody(query)
        .withHeaders(AUTH_TOKEN_HEADER -> token)
      ).get

      status(second) mustBe OK
      contentType(second) mustBe Some("application/json")
      contentAsJson(second) mustEqual JsObject(Map(
        "id" -> JsString("id-A_1"),
        "job" -> JsString("id-A"),
        "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
        "output" -> JsString("file:/Volumes/tmp/out/"),
        "transtype" -> JsString("html5"),
        "params" -> JsObject(List.empty),
        "processing" -> JsString(now.toString),
        "worker" -> JsString("worker"),
        "status" -> JsString("process")
      ))

      route(app, FakeRequest(POST, "/api/v1/work")
        .withJsonBody(query)
        .withHeaders(AUTH_TOKEN_HEADER -> token)
      ).map(status) mustBe Some(NO_CONTENT)
    }

    "return XHTML job" in {
      val query = JsArray(List(
        JsString("xhtml")
      ))

      val home = route(app, FakeRequest(POST, "/api/v1/work")
        .withJsonBody(query)
        .withHeaders(AUTH_TOKEN_HEADER -> token)
      ).get

      status(home) mustBe NO_CONTENT
    }
  }

}
