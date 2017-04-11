package services

import javax.inject.{Inject, Singleton}

import play.api.Logger

import scala.collection.mutable

/**
  * Store for conversion log messages.
  */
trait LogStore {
  def add(id: String, log: Seq[String]): Unit
  def get(id: String): Option[Seq[String]]
}

/**
  * Memory backed log store.
  */
@Singleton
class SimpleLogStore extends LogStore {

  private val logger = Logger(this.getClass)

  val logs  = mutable.Map[String, Seq[String]]()
  override def add(id: String, log: Seq[String]): Unit = {
    logger.info(s"Add $id to log store: ${log.size} lines")
    logs += id -> log
  }
  override def get(id: String): Option[Seq[String]] = {
    logger.info("Log store has " + logs.keys.mkString(", "))
    logs.get(id)
  }
}