package apiimport.slickdb

import java.sql.Timestamp

import apiimport.manifests.VoyageManifestParser
import apiimport.manifests.VoyageManifestParser.VoyageManifest
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext
import scala.util.Try


case class VoyageManifestPassengerInfoTable(tables: Tables) {
  val log = Logger(getClass)

  import tables.profile.api._
  import tables.{VoyageManifestPassengerInfo, VoyageManifestPassengerInfoRow}

  def rowsToInsert(vm: VoyageManifest, dayOfWeek: Int, weekOfYear: Int, jsonFile: String)(implicit ec: ExecutionContext): DBIOAction[Unit, NoStream, Effect.Write] = {
    val rows = voyageManifestRows(vm, dayOfWeek, weekOfYear, jsonFile)
    DBIO.seq(VoyageManifestPassengerInfo ++= rows)
  }

  def voyageManifestRows(vm: VoyageManifest, dayOfWeek: Int, weekOfYear: Int, jsonFile: String): List[VoyageManifestPassengerInfoRow] = {
    val schTs = new Timestamp(vm.scheduleArrivalDateTime.map(_.millisSinceEpoch).getOrElse(0L))

    vm.bestPassengers.map { passenger => passengerRow(vm, dayOfWeek, weekOfYear, schTs, passenger, jsonFile) }
  }

  def passengerRow(vm: VoyageManifest, dayOfWeek: Int, weekOfYear: Int, schTs: Timestamp, p: VoyageManifestParser.PassengerInfoJson, jsonFile: String): tables.VoyageManifestPassengerInfoRow = {
    VoyageManifestPassengerInfoRow(
      vm.EventCode,
      vm.ArrivalPortCode,
      vm.DeparturePortCode,
      vm.VoyageNumber.toInt,
      vm.CarrierCode,
      schTs,
      dayOfWeek,
      weekOfYear,
      p.DocumentType.getOrElse(""),
      p.DocumentIssuingCountryCode,
      p.EEAFlag,
      p.Age.flatMap(maybeAge => Try(maybeAge.toInt).toOption).getOrElse(-1),
      p.DisembarkationPortCode.getOrElse(""),
      p.InTransitFlag,
      p.DisembarkationPortCountryCode.getOrElse(""),
      p.NationalityCountryCode.getOrElse(""),
      p.PassengerIdentifier.getOrElse(""),
      p.InTransitFlag match {
        case "Y" => true
        case _ => false
      },
      jsonFile
    )
  }

  def dayOfWeekAndWeekOfYear(date: Timestamp)(implicit ec: ExecutionContext): DBIOAction[Option[(Int, Int)], NoStream, Effect] =
    sql"""SELECT EXTRACT(DOW FROM TIMESTAMP'#$date'), EXTRACT(WEEK FROM TIMESTAMP'#$date')""".as[(Int, Int)].map(_.headOption)
}
