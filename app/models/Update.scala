package models

// XXX Why is status optional?
sealed case class Update(id: String, status: Option[StatusString])

object Update {

}
