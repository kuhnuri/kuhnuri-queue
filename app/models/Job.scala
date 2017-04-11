package models

import generated.enums.Status

sealed case class Job(id: String, input: String, output: String, transtype: String,
                      params: Map[String, String],
                      status: StatusString) {
  //  def toJobStatus(status: StatusString): JobStatus = {
  //    JobStatus(id, output, status)
  //  }
}

sealed case class Create(input: String, output: String, transtype: String, params: Map[String, String]) {
}

sealed case class JobResult(job: Job, log: Seq[String])

sealed case class Update(id: String, status: Option[StatusString])

//sealed case class JobStatus(id: String, output: Option[String], status: StatusString)

sealed trait StatusString

object StatusString {

  case object Queue extends StatusString {
    override val toString = "queue"
  }

  case object Process extends StatusString {
    override val toString = "process"
  }

  case object Done extends StatusString {
    override val toString = "done"
  }

  case object Error extends StatusString {
    override val toString = "error"
  }

  def parse(status: String): StatusString = status match {
    case "queue" => Queue
    case "process" => Process
    case "done" => Done
    case "error" => Error
    case s: String => throw new IllegalArgumentException(s)
  }

  def parse(status: Status): StatusString = status match {
    case Status.queue => Queue
    case Status.process => Process
    case Status.done => Done
    case Status.error => Error
  }
}
