package models.request

import models.StatusString

case class Filter(status: Option[StatusString])
