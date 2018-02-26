package models

sealed case class Update(id: String, status: Option[StatusString])

object Update {

}
