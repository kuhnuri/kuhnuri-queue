package models

import java.time.LocalDateTime
import java.util.UUID

/**
  * Job storage.
  *
  * @param id        job ID
  * @param input     input file
  * @param output    output file
  * @param transtype transformation type
  * @param params    DITA-OT parameters
  * @param status    status of the conversion
  */
sealed case class JobRow(id: String, input: String, output: String, transtype: String,
                         params: Map[String, String], status: StatusString, created: LocalDateTime) {
  //  def toJobStatus(status: StatusString): JobStatus = {
  //    JobStatus(id, output, status)
  //  }

  def toJob: Job = {
    Job(id, input, output, transtype, params, status)
  }
}

object JobRow {
  def apply(job: Create): JobRow =
    JobRow(UUID.randomUUID().toString, job.input, job.output, job.transtype, job.params, StatusString.Queue, LocalDateTime.now())
}