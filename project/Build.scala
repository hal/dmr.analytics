import sbt.Keys._
import sbt._

object BuildSettings {

  val Name = "dmr-analytics"
  val Version = "0.0.2"
  val ScalaVersion = "2.11.1"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    name := Name,
    version := Version,
    scalaVersion := ScalaVersion,
    organization := "org.jboss",
    description := "Utilities to analyze the WildFly management model ",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint")
  )
}

object Resolvers {
  val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  val sonatype = "Sonatype Release" at "https://oss.sonatype.org/content/repositories/releases"
  val mvnrepository = "MVN Repo" at "http://mvnrepository.com/artifact"
  val jboss = "JBoss Repository" at "https://repository.jboss.org/nexus/content/groups/public/"

  val allResolvers = Seq(typesafe, sonatype, mvnrepository, jboss)

}

object Dependency {

  object Version {
    val DmrRepl = "0.2.3"
    val ScalaCheck = "1.11.3"
    val ScalaLogging = "2.1.2"
    val ScalaTest = "2.1.4"
  }

  val csv = "com.rockymadden.delimited" % "delimited-core_2.10" % "0.1.0"
  val dmrRepl = "org.jboss" %% "dmr-repl" % Version.DmrRepl
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % Version.ScalaLogging

  val scalaTest = "org.scalatest" %% "scalatest" % Version.ScalaTest % "test"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % Version.ScalaCheck % "test"
}

object Dependencies {

  import Dependency._

  val dmrAnalytics = Seq(logback, scalaLogging, csv, dmrRepl, scalaTest, scalaCheck)
}

object DmrAnalyticsBuild extends Build {

  import BuildSettings._
  import Resolvers._

  lazy val dmrAnalytics = Project(
    id = "dmranalytics",
    base = file("."),
    settings = buildSettings ++ Seq(
      resolvers := allResolvers,
      libraryDependencies ++= Dependencies.dmrAnalytics,
      initialCommands += """
        |import scala.concurrent.ExecutionContext.Implicits.global
        |import scala.language.implicitConversions
        |import org.jboss.dmr.scala._
        |import org.jboss.dmr.scala.ModelNode
        |import org.jboss.dmr.scala._
        |import org.jboss.dmr.repl._
        |import org.jboss.dmr.repl.Client._
        |import org.jboss.dmr.repl.Storage._
        |import org.jboss.dmr.analytics._
                         """.stripMargin,
      mainClass := Some("run")))
}
