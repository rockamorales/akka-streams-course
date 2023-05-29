package part2_primer

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {
  implicit val system = ActorSystem("OperatorFusion")

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map((_ + 1))
  val simpleFlow2 = Flow[Int].map((_ * 10))
  val simpleSink = Sink.foreach[Int](println)

  // if you connect components using via method, or viaMat, to or twoMap methods the composite akka streams
  // will by default run on the same actor
  // This ---v runs on the same actor
//  simpleSource.via(simpleFlow2).to(simpleSink).run()
  // it is called operator/component FUSION

  // equivalent to
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        val x2 = x + 1
        val y = x2 * 10
        println(y)
    }
  }

//  val simpleActor = system.actorOf(Props[SimpleActor])
//  (1 to 1000).foreach(simpleActor ! _)

  // what happens with complex flows with costly operations

  val complexFlow = Flow[Int].map { x =>
    //simulating a long computation
    Thread.sleep(1000)
    x + 1
  }

  val complexFlow2 = Flow[Int].map { x =>
    //simulating a long computation
    Thread.sleep(1000)
    x * 10
  }

//  simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink).run()

  //async boundary
//  simpleSource.via(complexFlow).async // runs on a one actor
//    .via(complexFlow2).async // run
//    .to(simpleSink)
//    .run()

  // ordering guarantees
  Source(1 to 3)
    .map(element => {
      println(s"Flow A: $element")
      element
    }).async
    .map(element => {
      println(s"Flow B: $element")
      element
    }).async
    .map(element => {
      println(s"Flow C: $element")
      element
    }).async
    .runWith(Sink.ignore)
}
