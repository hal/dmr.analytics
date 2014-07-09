package org.jboss.dmr.analytics

import java.io.{BufferedWriter, FileWriter}

import com.rockymadden.delimited.Write.DelimitedWriter
import com.typesafe.scalalogging.slf4j.Logger
import org.jboss.dmr.repl.Client._
import org.slf4j.LoggerFactory

object Main {

  def main(args: Array[String]) {

    val logger = Logger(LoggerFactory.getLogger("org.jboss.dmr.analytics.Main"))

    // read attributes
    val con = ("localhost", 9990)
    logger.info(s"Reading attributes from $con")
    val client = connect(con._1, con._2)
    val sequencer = new Sequencer(client)
    val attributes = sequencer.read()
    client.close()
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
