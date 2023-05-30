package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Failure

object IntegratingWithActors extends App {
  implicit val system = ActorSystem("IntegratingWithActors")

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string: ${s}")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number: $n")
        sender() ! (2 * n)
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
  val numbersSource = Source(1 to 10)

  //actor as a flow
  implicit val timeout = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

  numbersSource.via(actorBasedFlow).to(Sink.ignore).run()
  numbersSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore).run() // equivalent

  // Actor as a source
  // deprecated method actorRef
//  val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)

  // use alternative wit completion strategy and failure as input params
  val completion: PartialFunction[Any, CompletionStrategy] = {
    case _ => CompletionStrategy.draining
  }
  val failure: PartialFunction[Any, Throwable] = {
    case _ => new Exception()
  }
  val actorPoweredSource = Source.actorRef[Int](completion, failure, bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
  val materializedActorRef = actorPoweredSource.to(Sink.foreach[Int](number => println(s"Actor powered flow got number: $number"))).run()
  materializedActorRef ! 10
  // terminating the stream
  materializedActorRef ! akka.actor.Status.Success("complete")

  /*
    Actor as a destination/sink
      - an init message
      - an ack message to confirm the reception
      - a complete message
      - a function to generate a message in case the steams throws an exception

   */

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)
  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream failed: $ex")
      case message =>
        log.info(s"Message $message has come to its final resting point")
        sender() ! StreamAck
    }
  }

  val destinationActor = system.actorOf(Props[DestinationActor], "detinationActor")

  val actorPoweredSink = Sink.actorRefWithBackpressure[Int](
      destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    ackMessage = StreamAck,
    onFailureMessage = ex => StreamFail(ex)
  )

  Source(1 to 10).to(actorPoweredSink).run()

  // not seding the acknowledgement message back will be interpreted as a backpressure

  // Sink.actorRef() not recommended -- does not provide backpressure

}
