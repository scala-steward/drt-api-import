package advancepassengerinfo.importer.slickdb.tables

import advancepassengerinfo.importer.slickdb.DatabaseImpl.profile.api._
import slick.lifted.{Rep, Tag}

import java.sql.Timestamp

class ProcessedJsonTable(_tableTag: Tag) extends Table[ProcessedJsonRow](_tableTag, Option("public"), "processed_json") {
  val zip_file_name: Rep[String] = column[String]("zip_file_name")
  val json_file_name: Rep[String] = column[String]("json_file_name")
  val suspicious_date: Rep[Boolean] = column[Boolean]("suspicious_date")
  val success: Rep[Boolean] = column[Boolean]("success")
  val processed_at: Rep[Timestamp] = column[Timestamp]("processed_at")
  val arrival_port_code: Rep[Option[String]] = column[String]("arrival_port_code")
  val departure_port_code: Rep[Option[String]] = column[String]("departure_port_code")
  val voyage_number: Rep[Option[Int]] = column[Int]("voyage_number")
  val scheduled: Rep[Option[Timestamp]] = column[Timestamp]("scheduled_date")
  val event_code: Rep[Option[String]] = column[String]("event_code")
  val non_interactive_total_count: Rep[Option[Int]] = column[Int]("non_interactive_total_count")
  val non_interactive_trans_count: Rep[Option[Int]] = column[Int]("non_interactive_trans_count")
  val interactive_total_count: Rep[Option[Int]] = column[Int]("interactive_total_count")
  val interactive_trans_count: Rep[Option[Int]] = column[Int]("interactive_trans_count")

  def * = (zip_file_name, json_file_name, suspicious_date, success, processed_at,
    arrival_port_code, departure_port_code, voyage_number, scheduled,
    event_code, non_interactive_total_count, interactive_total_count, non_interactive_trans_count, interactive_trans_count) <> (ProcessedJsonRow.tupled, ProcessedJsonRow.unapply)
}
