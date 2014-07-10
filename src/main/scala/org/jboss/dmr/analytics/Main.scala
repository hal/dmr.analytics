package org.jboss.dmr.analytics

import java.io.{BufferedWriter, FileWriter}

import com.rockymadden.delimited.Write.DelimitedWriter
import com.typesafe.scalalogging.slf4j.Logger
import org.jboss.dmr.repl.Response._
import org.jboss.dmr.repl.{Response, Client}
import org.jboss.dmr.repl.Client._
import org.jboss.dmr.scala._
import org.slf4j.LoggerFactory

object Main {

  val logger = Logger(LoggerFactory.getLogger("org.jboss.dmr.analytics.Main"))

  def main(args: Array[String]) {
    // if present take host and port from the command line
    val con = if (args.length == 2)
      (args(0), args(1).toInt)
    else
      ("localhost", 9990)
    val client = connect(con._1, con._2)

    readServerInfo(client)
    readAttributes(client)

    client.close()
  }

  def readServerInfo(client: Client) {
    client ! (ModelNode() at root op 'read_resource('include_runtime -> true)) map {
      case Response(Success, result) =>

        // Read release and management version
        val serverInfo = IndexedSeq(
            result.get("release-codename") flatMap (_.asString) getOrElse "n/a",
            result.get("release-version") flatMap (_.asString) getOrElse "n/a",
            result.get("process-type") flatMap (_.asString) getOrElse "n/a",
            result.get("management-major-version") flatMap (_.asString) getOrElse "n/a",
            result.get("management-micro-version") flatMap (_.asString) getOrElse "n/a",
            result.get("management-minor-version") flatMap (_.asString) getOrElse "n/a")

        val releaseFilename = "output/server.csv"
        logger.info(s"Writing CSV to $releaseFilename")
        DelimitedWriter.using(new BufferedWriter(new FileWriter(releaseFilename))) {
          writer =>
            writer.writeLine(Some(IndexedSeq("name", "version", "process-type", "major", "micro", "minor")))
            writer.writeLine(Some(serverInfo))
        }

      case Response(Failure, failure) =>
        val error = failure.asString getOrElse "undefined"
        logger.error(s"Error reading version: $error")
    }
  }

  def readAttributes(client: Client) {
    val allAttributes = new Sequencer(client).read()

    // filter attributes
    def any[A](predicates: (A => Boolean)*): A => Boolean =
      a => predicates.exists(predicate => predicate(a))

    val deployments: (DmrAttribute) => Boolean =
      dmrAttribute => dmrAttribute.address.tuple.contains("deployment" -> "*") && dmrAttribute.address.tuple.size > 1
    // TODO filter anything else?
    val (filtered, attributes) = allAttributes.partition(any(deployments))
    logger.debug(s"Filtered resources:\n${filtered.map(a => (a.address, a.name)).mkString("\n")}")
    logger.debug(s"Relevant resources:\n${attributes.map(a => (a.address, a.name)).mkString("\n")}")
    logger.info(s"Read ${attributes.size} attributes")

    if (attributes.nonEmpty) {
      // transform to csv
      val header = IndexedSeq(
        "address",
        "name",
        "type",
        "valueType",
        "valueTypeDepth",
        "storage",
        "accessType",
        "allowNull",
        "hasDefault",
        "hasAlternatives",
        "hasAliases",
        "isEnum",
        "restartPolicy",
        "deprecated")

      val data = attributes.map(dmrAttribute => {
        val address = dmrAttribute.address
        val metaData = dmrAttribute.metaData
        IndexedSeq(
          s"$address",
          s"${dmrAttribute.name}",
          s"${dmrAttribute.`type`}",
          s"${metaData.valueType}",
          s"${metaData.valueTypeDepth}",
          s"${metaData.storage}",
          s"${metaData.accessType}",
          s"${metaData.allowNull}",
          s"${metaData.hasDefaultValue}",
          s"${metaData.alternatives.nonEmpty}",
          s"${metaData.alias.isDefined}",
          s"${metaData.allowedValues.nonEmpty}",
          s"${metaData.restartPolicy.toString}",
          s"${metaData.deprecated}")
      })

      // write csv
      val filename = "output/attributes.csv"
      logger.info(s"Writing CSV to $filename")
      DelimitedWriter.using(new BufferedWriter(new FileWriter(filename))) {
        writer =>
          writer.writeLine(Some(header))
          writer.writeAll(Some(data))
      }
    }
  }
}
