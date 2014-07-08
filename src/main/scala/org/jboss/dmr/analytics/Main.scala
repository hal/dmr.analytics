package org.jboss.dmr.analytics

import com.typesafe.scalalogging.slf4j.Logger
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.jboss.dmr.ModelType
import org.jboss.dmr.repl.Client._
import org.jboss.dmr.scala.Address
import org.slf4j.LoggerFactory
import spark.util.Timestamp

object Main {

  def main(args: Array[String]) {

    val logger = Logger(LoggerFactory.getLogger("org.jboss.dmr.analytics.Main"))
    val client = connect("localhost", 9990)
    val sequencer = new Sequencer(client)
    val data = sequencer.read()
    client.close()

    // Log attributes
    logger.info(s"Read ${data.size} attributes:")
//        val infos = data.sortBy(attribute => attribute.address) map (attribute => {
//          s"${attribute.name.padTo(40, ' ')}: ${attribute.`type`.name().padTo(10, ' ')} @ ${attribute.address} (${attribute.metaData.valueType.name()})"
//        })
//        infos.foreach(info => logger.debug(info))


    // Let's Spark...
    val sc = new SparkContext("local", "DMR Analytics")
    try {
      val attributes: RDD[DmrAttribute] = sc.parallelize(data)

      logger.info("Attributes grouped by resources")
      val resources: RDD[(Address, Iterable[DmrAttribute])] = attributes.groupBy(attribute => attribute.address).cache()
      val groupedResources: Array[(Address, Iterable[DmrAttribute])] = resources
        .collect()
        .sortBy(addressToDmrAttributes => addressToDmrAttributes._1)

      groupedResources.foreach { case (address, dmrAttributes) => println(s"$address: ${dmrAttributes.size}")}

      logger.info("Attributes grouped by type")
      val types: RDD[(ModelType, Iterable[DmrAttribute])] = attributes.groupBy(attribute => attribute.`type`)
      types.foreach {
        case (address, dmrAttributes) => println(s"$address: ${dmrAttributes.size}")
      }

      // write the resource size CSV
      writeCSV[(Address, Iterable[DmrAttribute])]("resource_size", groupedResources,
        () => "address,count",
        (item) => {
          val address = item._1
          val atts = item._2
          List(s"$address,${atts.size}")
        })

      // write the attribute type CSV
      writeCSV[(ModelType, Iterable[DmrAttribute])]("attribute_type", types.collect(),
        () => "type,count",
        (item) => {
          val modelType = item._1
          val atts = item._2
          List(s"$modelType,${atts.size}")
        })

      // the raw data
      writeCSV[(Address, Iterable[DmrAttribute])]("attributes", resources.collect(),
        () => "address,name,type,valueType,storage,accessType,allowNull,hasDefault,hasAlternatives,hasAliases,isEnum,restartPolicy",
        (item) => {
          val address = item._1.toString()
          val atts = item._2
          for(
            att <- atts
          ) yield s"$address,${att.name},${att.`type`},${att.metaData.valueType},${att.metaData.storage},${att.metaData.accessType},${att.metaData.allowNull},${att.metaData.hasDefaultValue},${att.metaData.alternatives.nonEmpty},${att.metaData.alias.isDefined},${att.metaData.allowedValues.nonEmpty},${att.metaData.restartPolicy.toString}"

        })

    } finally {
      sc.stop()
    }

    /**
     * Utility to export RDD's as CSV files
     *
     * @param prefix filename prefix
     * @param data the actual rdd
     * @param rowFn the conversion function to create CSV rows
     * @tparam T the RDD type
     */
    def writeCSV[T](prefix: String, data: Seq[T], colFn: () => String, rowFn: (T) => Iterable[String]) {
      val now = Timestamp.now()
      val fname= s"$prefix-$now.csv"

      import java.io._

      val out: File = new File("output", fname)
      val pw = new PrintWriter(out)

      try {
        // column names
        pw.write(s"${colFn()}\n")

        // rows
        data.foreach {
          case item: T => rowFn(item).map(row => pw.write(s"$row\n"))
        }

      }
      finally
      {
        pw.close
      }
    }
  }
}
