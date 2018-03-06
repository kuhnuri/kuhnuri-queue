import java.time._

import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.Helpers.{contentAsJson, contentType, _}
import play.api.test._
import services.{DummyQueue, Queue}

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
class ApplicationSpec extends PlaySpec with GuiceOneAppPerSuite {

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
      bind(classOf[Queue]).to(classOf[DummyQueue]),
      bind(classOf[Clock]).to(clock)
    )
    //    .overrides(bind(classOf[Dispatcher]).to(classOf[DefaultDispatcher]))
    .build

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
      val home = route(app, FakeRequest(GET, "/api/v1/jobs")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("application/json")
      sortById(contentAsJson(home)) mustEqual JsArray(Seq(
        JsObject(Map(
          "id" -> JsString("id-A"),
          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          "output" -> JsString("file:/Volumes/tmp/out/"),
          "transtype" -> JsString("html5"),
          "params" -> JsObject(List.empty),
          "status" -> JsString("queue"),
          "priority" -> JsNumber(0),
          "created" -> JsString(now.minusHours(1).toString)
//          "processing" -> JsNull,
//          "finished" -> JsNull
        )),
        JsObject(Map(
          "id" -> JsString("id-A1"),
          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          "output" -> JsString("file:/Volumes/tmp/out/"),
          "transtype" -> JsString("html5"),
          "params" -> JsObject(List.empty),
          "status" -> JsString("queue"),
          "priority" -> JsNumber(0),
          "created" -> JsString(now.minusHours(2).toString)
//          "processing" -> JsNull,
//          "finished" -> JsNull
        )),
        JsObject(Map(
          "id" -> JsString("id-B"),
          "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
          "output" -> JsString("file:/Volumes/tmp/out/"),
          "transtype" -> JsString("pdf"),
          "params" -> JsObject(List.empty),
          "status" -> JsString("queue"),
          "priority" -> JsNumber(0),
          "created" -> JsString(now.toString)
//          "processing" -> JsNull,
//          "finished" -> JsNull
        ))
      ))
    }

    "show job details" in {
      val home = route(app, FakeRequest(GET, "/api/v1/job/id-B")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("application/json")
      contentAsJson(home) mustEqual JsObject(Map(
        "id" -> JsString("id-B"),
        "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
        "output" -> JsString("file:/Volumes/tmp/out/"),
        "transtype" -> JsString("pdf"),
        "params" -> JsObject(List.empty),
        "status" -> JsString("queue"),
        "priority" -> JsNumber(0),
        "created" -> JsString(now.toString)
//        "processing" -> JsNull,
//        "finished" -> JsNull
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
          "transtype" -> JsString("html5"),
          "params" -> JsObject(List.empty)
        ))
        val created = route(app, FakeRequest(POST, "/api/v1/job").withJsonBody(body)).get

        status(created) mustBe CREATED
        contentType(created) mustBe Some("application/json")
        //        contentAsJson(created) mustEqual JsObject(Map(
        //            "id" -> JsString("id-A"),
        //            "input" -> JsString("file:/Users/jelovirt/Work/github/dita-ot/src/main/docsrc/userguide.ditamap"),
        //            "output" -> JsString("file:/Volumes/tmp/out"),
        //            "transtype" -> JsString("html5"),
        //            "params" -> JsObject(List.empty)
        //          ))

        val res = route(app, FakeRequest(GET, "/api/v1/jobs")).map(contentAsJson(_)).get.as[JsArray]
        res.value.size mustBe 4
      }
    }
  }

  //  "CountController" should {
  //
  //    "return an increasing count" in {
  //      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "0"
  //      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "1"
  //      contentAsString(route(app, FakeRequest(GET, "/count")).get) mustBe "2"
  //    }
  //
  //  }

}
