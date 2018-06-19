package services

import models.Worker

import scala.collection.mutable.Map

/** Worker storage. */
object WorkerStore {
  val workers: Map[String, Worker] = Map.empty
}
