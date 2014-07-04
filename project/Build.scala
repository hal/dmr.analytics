import sbt.Keys._
import sbt._

object BuildSettings {

  val Name = "dmr-analytics"
  val Version = "0.0.1"
  val ScalaVersion = "2.10.4"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    name := Name,
    version := Version,
    scalaVersion := ScalaVersion,
    organization := "com.typesafe",
    description := "Activator Spark Template",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint")
  )
}

object Resolvers {
  // This is a temporary location within the Apache repo for the 1.0.0-RC3
  // release of Spark.
  val apache = "Apache Repository" at "https://repository.apache.org/content/repositories/orgapachespark-1012/"
  val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  val sonatype = "Sonatype Release" at "https://oss.sonatype.org/content/repositories/releases"
  val mvnrepository = "MVN Repo" at "http://mvnrepository.com/artifact"
  val jboss = "JBoss Repository" at "https://repository.jboss.org/nexus/content/groups/public/"

  val allResolvers = Seq(apache, typesafe, sonatype, mvnrepository, jboss)

}

object Dependency {

  object Version {
    val Spark = "1.0.0"
    val ScalaTest = "2.1.4"
    val ScalaCheck = "1.11.3"
    val DmrRepl = "0.2.2"
  }

  val sparkCore = "org.apache.spark" %% "spark-core" % Version.Spark
  val sparkStreaming = "org.apache.spark" %% "spark-streaming" % Version.Spark
  val sparkRepl = "org.apache.spark" %% "spark-repl" % Version.Spark
  val dmrRepl = "org.jboss" %% "dmr-repl" % Version.DmrRepl

  val scalaTest = "org.scalatest" %% "scalatest" % Version.ScalaTest % "test"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % Version.ScalaCheck % "test"
}

object Dependencies {

  import Dependency._

  val dmrAnalytics = Seq(sparkCore, sparkStreaming, sparkRepl, dmrRepl, scalaTest, scalaCheck)
}

object DmrAnalyticsBuild extends Build {

  import BuildSettings._
  import Resolvers._

  lazy val dmrAnalytics = Project(
    id = "dmranalytics",
    base = file("."),
    settings = buildSettings ++ Seq(
      // runScriptSetting, 
      resolvers := allResolvers,
      libraryDependencies ++= Dependencies.dmrAnalytics,
      unmanagedResourceDirectories in Compile += baseDirectory.value / "conf",
      initialCommands += """
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.implicitConversions
  import org.jboss.dmr.scala._
  import org.jboss.dmr.scala.ModelNode
  import org.jboss.dmr.scala._
  import org.jboss.dmr.repl._
  import org.jboss.dmr.repl.Client._
  import org.jboss.dmr.repl.Storage._
  import org.jboss.dmr.analytics._
                         """,
      mainClass := Some("run"),
      // Must run Spark tests sequentially because they compete for port 4040!
      parallelExecution in Test := false))
}



