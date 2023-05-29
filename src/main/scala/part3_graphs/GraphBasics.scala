package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.concurrent.duration._
import scala.language.postfixOps

object GraphBasics extends App {
  implicit val system = ActorSystem("GraphBasics")

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1) // hard computation
  val multiplier = Flow[Int].map(x => x * 10) // hard computation

  val output = Sink.foreach[(Int, Int)](println)

  // step 1 - seeting up the fundamentals for the graph

  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

      import GraphDSL.Implicits._ // brings some nice operators into scope

      // step 2 - add the necessary components of this graph
      val broadcast = builder.add(Broadcast[Int](2)) //fan-out operator
      val zip = builder.add(Zip[Int, Int]) // fan-in operator

      // step 3 - tying up the components
      input ~> broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output

      //step 4 - return a closed shape
      ClosedShape

      // must return a shape
    } // graph - static graph
  ) // runnable graph
  // graph.run()

  /**
   * exercise 1: feed a source into 2 sinks at the same time (hint: use a broadcast)
   */
  val sink1 = Sink.foreach[Int](x => println(s"First sink: ${x}"))
  val sink2 = Sink.foreach[Int](x => println(s"Second sink: ${x}"))
  val graph1 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

//      input ~> broadcast
//
//      broadcast.out(0) ~> sink1
//      broadcast.out(1) ~> sink2

      // alternative approach
      input ~> broadcast ~> sink1 // called implicit port numbering
               broadcast ~> sink2

      ClosedShape
    }
  )
//  graph1.run()

  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)
  val firstSink = Sink.fold[Int, Int](0)((count, _) => {
    println(s"First sink number of elements: ${count}")
    count + 1
  })
  val secondSink = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Second sink number of elements: ${count}")
    count + 1
  })

  val graph2 = RunnableGraph.fromGraph(
    GraphDSL.create(){ implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      slowSource ~> merge ~> balance ~> firstSink
      fastSource ~> merge
      balance ~> secondSink


      ClosedShape
    }
  )
  graph2.run()
}
