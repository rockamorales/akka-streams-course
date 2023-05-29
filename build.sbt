ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"
lazy val akkaVersion = "2.6.10"
lazy val scalaTestVersion = "3.1.4"

lazy val root = (project in file("."))
  .settings(
    name := "udemy-akka-streams",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion
    )
  )
