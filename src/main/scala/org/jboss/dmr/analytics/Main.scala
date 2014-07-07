package org.jboss.dmr.analytics

import com.typesafe.scalalogging.slf4j.Logger
import org.apache.spark.SparkContext
import org.jboss.dmr.repl.Client._
import org.jboss.dmr.scala._
import org.slf4j.LoggerFactory

object Main {

  def main(args: Array[String]) {

    val logger = Logger(LoggerFactory.getLogger("org.jboss.dmr.analytics.Main"))
    val client = connect("localhost", 9990)
    val sequencer = new Sequencer(client)
    val data = sequencer.read()
    client.close()

    // Log attributes
    logger.info(s"Read ${data.size} attributes:")
//    val infos = data.sortBy(attribute => attribute.address) map (attribute => {
//      s"${attribute.name.padTo(40, ' ')}: ${attribute.`type`.name().padTo(10, ' ')} @ ${attribute.address}"
//    })
//    infos.foreach(info => logger.debug(info))
//
    // Let's Spark...
    val sc = new SparkContext("local", "DMR Analytics")
    try {
      val attributes = sc.parallelize(data)

      logger.info("Attributes grouped by resources")
      val resources = attributes.groupBy(attribute => attribute.address)
      resources
        .collect()
        .sortBy(addressToDmrAttributes => addressToDmrAttributes._1)
        .foreach { case (address, dmrAttributes) => println(s"$address: ${dmrAttributes.size}")}

      logger.info("Attributes grouped by type")
      val types = attributes.groupBy(attribute => attribute.`type`)
      types.foreach {
        case (address, dmrAttributes) => println(s"$address: ${dmrAttributes.size}")
      }

    } finally {
      sc.stop()
    }
  }
}
