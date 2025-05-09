package advancepassengerinfo.importer.provider

import advancepassengerinfo.health.LastCheckedState
import advancepassengerinfo.importer.DqApiFeedImpl
import advancepassengerinfo.importer.processor.DqFileProcessor
import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.stream.{KillSwitches, UniqueKillSwitch}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import drtlib.SDate
import metrics.MetricsCollectorLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

case class MockDqFileProcessor(probe: ActorRef) extends DqFileProcessor {
  override def process(zipFileName: String): Source[Option[(Int, Int)], Any] = {
    probe ! zipFileName
    Source(List(Option((1, 1))))
  }
}

case class MockFileNames(files: List[List[String]]) extends FileNames {
  private var filesQueue = files
  override val nextFiles: String => Future[List[String]] = (previous: String) => filesQueue match {
    case Nil => Future.successful(List(previous))
    case head :: tail =>
      filesQueue = tail
      val files = if (previous.nonEmpty) previous :: head else head
      Future.successful(files)
  }
}

object MockStatsDCollector extends MetricsCollectorLike {
  override def counter(name: String, value: Double): Unit = {}
}

class DqApiFeedImplSpec extends TestKit(ActorSystem("MySpec"))
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  "An importer" should {
    "send all the files from an s3 file name provider in sequence" in {
      val filesProbe = TestProbe("files")

      val batchedFileNames = List(List("a", "b"), List("c", "d"), List("e", "f"))
      val mockFileNames = MockFileNames(batchedFileNames)
      val mockProcessor = MockDqFileProcessor(filesProbe.ref)
      val importer = DqApiFeedImpl(mockFileNames, mockProcessor, 100.millis, MockStatsDCollector, LastCheckedState(() => SDate.now()))

      val killSwitch: UniqueKillSwitch = importer.processFilesAfter("").viaMat(KillSwitches.single)(Keep.right).toMat(Sink.ignore)(Keep.left).run()

      batchedFileNames.flatten.foreach(f => filesProbe.expectMsg(f))

      killSwitch.shutdown()
    }
  }
}
