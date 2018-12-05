package controllers

import javax.inject._
import play.api.Logger
import play.api.mvc._

@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  def index = Action {
    Redirect("index.html")
  }

  def health = Action {
    logger.info("Check health")
    Ok("")
  }

}
