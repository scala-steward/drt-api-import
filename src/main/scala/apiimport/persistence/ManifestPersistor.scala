package apiimport.persistence

import java.sql.Timestamp

import akka.NotUsed
import akka.stream.scaladsl.Source
import apiimport.manifests.VoyageManifestParser.VoyageManifest
import drtlib.SDate
import org.slf4j.{Logger, LoggerFactory}
import apiimport.slickdb.{ProcessedManifestSourceTable, VoyageManifestPassengerInfoTable}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex


case class ManifestPersistor(tables: apiimport.slickdb.Tables)(implicit ec: ExecutionContext) {
  val log: Logger = LoggerFactory.getLogger(getClass)

  val dqRegex: Regex = "drt_dq_([0-9]{2})([0-9]{2})([0-9]{2})_[0-9]{6}_[0-9]{4}\\.zip".r

  val manifestTable = VoyageManifestPassengerInfoTable(tables)
  val sourceTable = ProcessedManifestSourceTable(tables)
  val db: tables.profile.backend.Database = tables.profile.api.Database.forConfig("db")

  import tables.profile.api._

  def zipFileDate(fileName: String): Option[SDate] = fileName match {
    case dqRegex(year, month, day) => Option(SDate(s"20$year-$month-$day"))
    case _ => None
  }

  val oneDayMillis: Long = 60 * 60 * 24 * 1000L

  def addPersistence(manifestsAndFailures: Source[(String, List[(String, VoyageManifest)], List[(String, String)]), NotUsed]): Source[Int, NotUsed] = manifestsAndFailures
    .mapConcat {
      case (zipFile, manifests, _) => manifests.map { case (jsonFile, vm) => (zipFile, jsonFile, vm) }
    }
    .mapAsync(12) {
      case (zipFile, jsonFile, vm) => addDowWoy(zipFile, jsonFile, vm)
    }
    .mapAsync(6) {
      case Some((zf, jf, vm, dow, woy)) => removeExisting(zf, jf, vm, dow, woy)
      case None => Future(None)
    }
    .mapAsync(6) {
      case Some((zf, jf, vm, dow, woy)) =>
        val eventualUnit = db.run(manifestTable.rowsToInsert(vm, dow, woy, jf))
        eventualUnit.flatMap {
          _ =>
            val maybeSuspiciousDate: Option[Boolean] = for {
              zipDate <- zipFileDate(zf)
              scdDate <- vm.scheduleArrivalDateTime
            } yield {
              scdDate.millisSinceEpoch - zipDate.millisSinceEpoch > 2 * oneDayMillis
            }
            val suspiciousDate = maybeSuspiciousDate.getOrElse(false)
            val processedAt = new Timestamp(SDate.now().millisSinceEpoch)
            val processedJsonFileToInsert = tables.ProcessedManifestSource += tables.ProcessedManifestSourceRow(zf, jf, suspiciousDate, processedAt)
            db.run(processedJsonFileToInsert)
        }
      case None => Future(0)
    }

  def removeExisting(zf: String, jf: String, vm: VoyageManifest, dow: Int, woy: Int): Future[Option[(String, String, VoyageManifest, Int, Int)]] = {
    val schTs = new Timestamp(vm.scheduleArrivalDateTime.map(_.millisSinceEpoch).getOrElse(0L))
    val value = tables.VoyageManifestPassengerInfo.filter(r => {
      r.event_code === vm.EventCode &&
        r.arrival_port_code === vm.ArrivalPortCode &&
        r.departure_port_code === vm.DeparturePortCode &&
        r.scheduled_date === schTs &&
        r.voyage_number === vm.VoyageNumber.toInt
    })

    db.run(value.delete).map(deletedCount => {
      if (deletedCount > 0) log.info(s"Removed $deletedCount existing entries")
      Option(zf, jf, vm, dow, woy)
    })
  }

  def addDowWoy(zipFile: String, jsonFile: String, vm: VoyageManifest): Future[Option[(String, String, VoyageManifest, Int, Int)]] = {
    val schTs = new Timestamp(vm.scheduleArrivalDateTime.map(_.millisSinceEpoch).getOrElse(0L))

    db.run(manifestTable.dayOfWeekAndWeekOfYear(schTs)).map {
      case Some((dow, woy)) => Option((zipFile, jsonFile, vm, dow, woy))
      case None => None
    }
  }

  def lastPersistedFileName: Future[Option[String]] = {
    val sourceFileNamesQuery = tables.ProcessedManifestSource.map(_.source_file_name)
    db.run(sourceFileNamesQuery.max.result)
  }
}