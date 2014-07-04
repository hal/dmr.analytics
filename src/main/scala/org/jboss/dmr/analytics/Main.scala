package org.jboss.dmr.analytics

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.jboss.dmr.repl.Client._
import org.jboss.dmr.scala._

object Main {

  def main(args: Array[String]) {
    val client = connect()
    val sequencer = new Sequencer(client)
    val data = sequencer.read("subsystem" -> "datasources")
    client.close()

        println(s"\n\nRead ${data.size} attributes:")
        val attributeStrings = data map (attribute => {
          s"${attribute.name.padTo(40, ' ')}: ${attribute.`type`.`type`.padTo(10, ' ')} @ ${attribute.address}"
        })
        attributeStrings.sorted.foreach(println(_))

    // Enter Spark
    val sc = new SparkContext("local", "DMR Analytics")
    try {
      val attributes = sc.parallelize(data)
      val resources = attributes.groupBy(attr => attr.address)

      println("Grouped by resources")


      resources.collect().sortBy(
        item => item._1.toString()
      ).foreach((tpl: (Address, Iterable[DmrAttribute])) => {
        println(s"${tpl._1}: ${tpl._2.size}")
      })


      println("Key by attribute type")
      val types: RDD[(AttributeType, Iterable[DmrAttribute])] = attributes.groupBy(att => att.`type`)
      types.foreach((tpl: (AttributeType, Iterable[DmrAttribute])) => {
        println(s"${tpl._1}: ${tpl._2.size}")
      })
      
    } finally {
      sc.stop()
    }
  }
}
