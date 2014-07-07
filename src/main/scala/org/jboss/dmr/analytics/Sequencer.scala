package org.jboss.dmr.analytics

import com.typesafe.scalalogging.slf4j.Logging
import org.jboss.dmr.ModelType
import org.jboss.dmr.ModelType.{LIST, OBJECT}
import org.jboss.dmr.repl.Response._
import org.jboss.dmr.repl.{Client, Response}
import org.jboss.dmr.scala._

import scala.collection.mutable.ListBuffer

/**
 * Enters the management model at the specified entry point (or at the root if no entry point is given);
 * iterates over all attributes of all resources and turns them into a list of [[DmrAttribute]].
 */
class Sequencer(client: Client) extends Logging {

  def read(entryPoint: Address = root): List[DmrAttribute] = {

    /**
     * Reads the attributes and nested child resources of the specified address.
     * Prepends the attributes to the specified list.
     */
    def rrd(address: Address, level: Int, attributes: ListBuffer[DmrAttribute]) {
      val rrdOp = ModelNode() at address op 'read_resource_description
      logger.debug(s"Read resource description for $address")

      client ! rrdOp map {
        case Response(Success, result: ModelNode) =>
          // the result comes in two flavours:
          //   - simple model node for none-wildcard rrd operations
          //   - list model node for wildcard rrd operations
          result match {

            case ModelNode(OBJECT) =>
              logger.debug(s"resource description for $address is a simple model node")
              readAttributes(address, result)

            case ModelNode(LIST) =>
              logger.debug(s"resource description(s) for $address are nested inside a list with ${result.values.size} element(s)")
              // Turn rrd-nodes into tuples of (rrd-address, rrd-result)
              val rrdTuples = result.values.map(rrdNode => {
                val outcome = for {
                  outcomeNode <- rrdNode.get("outcome")
                  outcome <- outcomeNode.asString
                } yield outcome

                val possibleTuple = outcome match {
                  case Some(Success) =>
                    val addressTuples = rrdNode.get("address").flatMap(_.asList) getOrElse Nil
                    val path = for {
                      addressTuple <- addressTuples
                      key = addressTuple.keys.head
                      value = addressTuple.values.head.asString.get
                    } yield key -> value
                    val rrdAddress = Address(path)
                    val rrdResult = rrdNode.getOrElse("result", ModelNode.Undefined)
                    Some(Pair(rrdAddress, rrdResult))

                  case _ =>
                    logger.error(s"Error reading description element ${rrdNode.asString.getOrElse("undefined")}")
                    None
                }
                possibleTuple.get // only return the successful tuples
              })

              // Split the tuples into a wildcard and none-wildcard list
              val (wildcardTuples, noneWildcardTuples) = rrdTuples.partition(tuple => {
                val adr = tuple._1
                val lastPart = adr.tuple.reverse.head._2
                lastPart.equals("*")
              })
              if (wildcardTuples.nonEmpty) {
                // if there were wildcard tuples, process only the first one
                val (rrdAddress, rrdResult) = wildcardTuples.head
                logger.debug(s"Proceed with the first wildcard address $rrdAddress")
                readAttributes(rrdAddress, rrdResult)
              } else {
                // otherwise process all none-wildcard tuples
                logger.debug(s"Proceed with all none-wildcard addresses ${noneWildcardTuples.map(_._1)}")
                noneWildcardTuples.foreach {
                  case (rrdAddress, rrdResult) => readAttributes(rrdAddress, rrdResult)
                }
              }

            case _ =>
              logger.error(s"Undefined result for read-resource-description @ $address")
              ModelNode.Undefined
          }

        case Response(Failure, failure: ModelNode) =>
          val error = failure.asString getOrElse "undefined"
          logger.error(s"Error reading description @ $address: $error")
          // report an error as special DmrAttribute instance
          attributes.prepend(DmrAttribute.error(error, address))
      }

      /** Read the attributes of a single rrd result node */
      def readAttributes(rrdAddress: Address, rrdResult: ModelNode): Unit = {
        val dmrAttributes = rrdResult.get("attributes") match {
          case Some(currentAttributes: ModelNode) =>
            logger.debug(s"Found ${currentAttributes.size} attributes @ $rrdAddress")
            currentAttributes map {
              case (attributeName: String, metaData: ModelNode) =>
                logger.debug(s"Creating DmrAttribute($attributeName)")
                val typeValue = metaData.get("type").flatMap(_.asString)
                val modelType = ModelType.valueOf(typeValue.getOrElse("UNDEFINED"))
                DmrAttribute(attributeName, modelType, rrdAddress, MetaData.parse(metaData, level))
            }
          case None =>
            logger.warn(s"No attributes found for read-resource-description @ $rrdAddress")
            Nil
        }
        attributes.prependAll(dmrAttributes)
        if (dmrAttributes.nonEmpty) logger.debug(s"Added ${dmrAttributes.size} attributes. New size: ${attributes.size}")

        // read children types and turn them into a list of strings
        val childTypes = rrdResult.get("children") match {
          case Some(children: ModelNode) =>
            children map {
              case (childType: String, _: ModelNode) => childType
            }
          case None => Nil
        }
        if (childTypes.nonEmpty) logger.debug(s"Found ${childTypes.size} children types: $childTypes")
        childTypes.foreach(childType => readChildren(rrdAddress, childType))
      }

      /**
       * Read the children of the specified type either by using a wildcard or
       * by explicitly iterating over all children.
       * Reading children means calling [[rrd()]] recursively.
       */
      def readChildren(rrdAddress: Address, childType: String): Unit = {
        // Check wildcard support
        val childAddress = rrdAddress / (childType -> "*")
        val wildcardTestOp = ModelNode() at childAddress op 'read_resource_description
        val supportsWildcard = client ! wildcardTestOp map {
          case Response(Success, _) => true
          case Response(Failure, _) => false
        }

        if (supportsWildcard.get) {
          // If wildcards are supported we assume that all siblings support the same attributes (i.e. belong to
          // the same resource type) and thus read only the wildcard address
          logger.debug(s"Wildcards are supported for $childAddress")
          rrd(childAddress, level + 1, attributes)

        } else {
          // Without wildcard support we assume that the siblings belong to different resource types
          // and thus read them one by one
          logger.debug(s"No wildcard support for $childAddress")
          val childOp = ModelNode() at rrdAddress op 'read_children_names('child_type -> childType)
          client ! childOp map {
            case Response(Success, result: ModelNode) =>
              result.asList match {

                case Some(children) =>
                  val childrenAddresses = for {
                    child <- children
                    childName <- child.asString
                  } yield address / (childType -> childName)
                  childrenAddresses.foreach(rrd(_, level + 1, attributes))

                case None =>
                  logger.warn("Found no child names for for type $childType @ $address")
              }
            case Response(Failure, failure: ModelNode) =>
              logger.error(s"Unable to read child names for type $childType @ $rrdAddress: ${failure.asString}")
          }
        }
      }
    }

    // start recursion
    val attributesBuffer = ListBuffer[DmrAttribute]()
    rrd(entryPoint, 0, attributesBuffer)

    // and return all collected attributes
    attributesBuffer.toList
  }
}
