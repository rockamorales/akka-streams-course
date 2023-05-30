package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FlowShape, OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip, ZipWith}

object GraphCycles extends App {
  import GraphDSL.Implicits._
  implicit val system = ActorSystem("GraphCycles")


  val accelerator = GraphDSL.create(){ implicit builder =>
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map{
      x =>
        println(s"Accelerating ${x}")
        x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape <~ incrementerShape
    ClosedShape
  }

//  RunnableGraph.fromGraph(accelerator).run()

  //graph cycle deadlock

  /*
    Solution 1: MergePreferred
   */

  val actualAccelerator = GraphDSL.create() { implicit builder =>
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map {
      x =>
        println(s"Accelerating ${x}")
        x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape
    ClosedShape
  }

//  RunnableGraph.fromGraph(actualAccelerator).run()

  /*
    Solution 2: buffer accelerator
   */

  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map {
      x =>
        println(s"Accelerating ${x}")
        Thread.sleep(100)
        x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape
    ClosedShape
  }

  RunnableGraph.fromGraph(bufferedRepeater).run()


  /*
    Cycles: risk deadlock
      - add bounds to the number of elements in the cycle

     Trade of: boundedness vs liveness
   */

  /**
   * Challenge: create a fan-in shape
   * - two inputs which will be fed with EXACTLY ONE number (1 and 1)
   * - output will emit an INFINITE FIBONACCI SEQUENCE based off those 2 numbers
   *
   * Hint: Use ZipWith and cycles, MergePreferred
   */
  val fibonacciFlowGraph = GraphDSL.create() { implicit builder =>
      // step 2 define auxiliary SHAPE
      val mergeShape = builder.add(MergePreferred[Int](1))
      val mergeShape1 = builder.add(MergePreferred[Int](1))
      val zipWithShape = builder.add(ZipWith[Int, Int, Int](
        (a, b) =>{
          Thread.sleep(500)
          a + b
        }
      )
      )
      val broadcast1 = builder.add(Broadcast[Int](2))
      val broadcast2 = builder.add(Broadcast[Int](2))
      val flowShape = builder.add(Flow[Int].map(x => x))
      mergeShape1 ~> broadcast1 ~> zipWithShape.in0
                     broadcast1 ~> mergeShape

      mergeShape ~> zipWithShape.in1
      zipWithShape.out ~> broadcast2 ~> mergeShape1
                          broadcast2 ~> flowShape

      UniformFanInShape(flowShape.out, mergeShape1.in(1), mergeShape.in(1))
    } // static graph

  val infiniteFibonacci = GraphDSL.create() { implicit builder =>
    val sourceShape = builder.add(Source(1 to 1))
    val broadcast = builder.add(Broadcast[Int](2))
    val fibonacciFlowGraphShape = builder.add(fibonacciFlowGraph)
    val sinkShape = builder.add(Sink.foreach[Int](println))
    sourceShape ~> broadcast ~> fibonacciFlowGraphShape
                   broadcast ~> fibonacciFlowGraphShape
    fibonacciFlowGraphShape ~> sinkShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(infiniteFibonacci).run()


  val infiniteFibonacciWithOneBroadcast = GraphDSL.create() { implicit builder =>
    val broadcast = builder.add(Broadcast[Int](3))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val mergeShape1 = builder.add(MergePreferred[Int](1))
    val zipWithShape = builder.add(ZipWith[Int, Int, Int](
      (a, b) => {
        Thread.sleep(500)
        a + b
      }
    ))
    mergeShape ~> broadcast ~> mergeShape1
                  broadcast ~> zipWithShape.in0

    mergeShape1 ~> zipWithShape.in1
    zipWithShape.out ~> mergeShape

    UniformFanInShape(broadcast.out(2), mergeShape.in(1), mergeShape1.in(1))
  }

  val fibonacciWithOneBroadcastGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      val broadcast = builder.add(Broadcast[Int](2))
      val sourceShape = builder.add(Source(1 to 1))
      val sinkShape = builder.add(Sink.foreach(println))
      val infiniteFibonacciWithOneBroadcastShape = builder.add(infiniteFibonacciWithOneBroadcast)
      sourceShape ~> broadcast ~> infiniteFibonacciWithOneBroadcastShape
                     broadcast ~> infiniteFibonacciWithOneBroadcastShape
      infiniteFibonacciWithOneBroadcastShape ~> sinkShape
      ClosedShape
    }
  )

  // fibonacciWithOneBroadcastGraph.run()

  val infiniteFibonacciUsingTupple = GraphDSL.create() { implicit builder =>
    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
    val mergeShape = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val zipShape = builder.add(Zip[BigInt, BigInt])
    val flowCalculatNext = builder.add(Flow[(BigInt, BigInt)].map(t => {
      Thread.sleep(500)
      (t._1 + t._2, t._1)
    }))
    val flowTakeLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))
    zipShape.out ~> mergeShape ~> flowCalculatNext ~> broadcast ~> flowTakeLast
                    mergeShape.preferred       <~     broadcast

    UniformFanInShape(flowTakeLast.out, zipShape.in0, zipShape.in1)
  }

  val fibonacciWithTuppleGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      val broadcast = builder.add(Broadcast[BigInt](2))
      val sourceShape = builder.add(Source.single[BigInt](1))
      val sinkShape = builder.add(Sink.foreach(println))
      val infiniteFibonacciUsingTuppleShape = builder.add(infiniteFibonacciUsingTupple)
      sourceShape ~> broadcast ~> infiniteFibonacciUsingTuppleShape
      broadcast ~> infiniteFibonacciUsingTuppleShape
      infiniteFibonacciUsingTuppleShape ~> sinkShape
      ClosedShape
    }
  )

//  fibonacciWithTuppleGraph.run()
}
