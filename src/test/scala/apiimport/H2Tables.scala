package apiimport

import apiimport.slickdb.Tables

object H2Db extends Db {

  object H2Tables extends {
    val profile = slick.jdbc.H2Profile
  } with Tables

  val tables: H2Tables.type = H2Tables
  val con: tables.profile.backend.Database = tables.profile.backend.Database.forConfig("tsql.db")
}
