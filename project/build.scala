import _root_.sbt._
import _root_.sbt.Keys._

object HikariCP_Plugin_Build extends Build {
  val main = Project("play-hikaricp-plugin", base = file(".")).settings(
    organization := "com.autma",
    version := "0.0.1",
    scalaVersion := "2.10.3",
    crossScalaVersions := Seq("2.10.3", "2.9.3"),
    resolvers ++= Seq(
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    ),
    libraryDependencies <+= scalaVersion({
      case "2.9.1" | "2.9.2" | "2.9.3" => "play" %% "play" % "[2.0,)" % "provided"
      case _ => "play" %% "play-jdbc" % "[2.0,)" % "provided"
    }),
    libraryDependencies ++= Seq(
      "com.zaxxer" % "HikariCP" % "1.2.6"
    )
  )
}
