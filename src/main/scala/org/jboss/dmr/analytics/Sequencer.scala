package org.jboss.dmr.analytics

import com.typesafe.scalalogging.slf4j.Logging
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
     * Reads the attributes and nested types of the specified address.
     * Prepends the attributes to the specified list.
     */
    def attributesAndChildren(address: Address, attributes: ListBuffer[DmrAttribute]) {
      val node = ModelNode() at address op 'read_resource_description
      logger.debug(s"Read resource description for $address. Collected attributes so far: ${attributes.size}")

      client ! node map {
        case Response(Success, result: ModelNode) =>
          // the result comes in two flavours:
          //   - simple model node for none-wildcard rrd operations
          //   - list model node for wildcard rrd operations
          val commonResult = result match {
            case ModelNode(OBJECT) =>
              logger.debug(s"resource description for $address is a simple model node")
              result
            case ModelNode(LIST) =>
              logger.debug(s"resource description for $address is nested inside a list")
              result.values.headOption match {
                case Some(head: ModelNode) => head.getOrElse("result", ModelNode.Undefined)
                case None => ModelNode.Undefined
              }
            case _ =>
              logger.error(s"Undefined result for read-resource-description @ $address")
              ModelNode.Undefined
          }

          // if there are attributes, read the key value tuples and turn them into a collection of DmrAttribute
          val dmrAttributes = commonResult.get("attributes") match {
            case Some(currentAttributes: ModelNode) =>
              logger.debug(s"Found ${currentAttributes.size} attributes @ $address")
              currentAttributes map {
                case (attributeName: String, metaData: ModelNode) =>
                  logger.debug(s"Creating DmrAttribute($attributeName)")
                  // TODO validate metadata (type, nilable, ...)
                  val typeValue = metaData.get("type").flatMap(_.asString)
                  DmrAttribute(attributeName, AttributeType(typeValue.getOrElse("UNDEFINED")), address, metaData)
              }
            case None =>
              logger.warn(s"No attributes found for read-resource-description @ $address")
              Nil
          }
          logger.debug(s"Adding ${dmrAttributes.size} attributes")
          attributes.prependAll(dmrAttributes)

          // read nested types
          val childrenTypes = commonResult.get("children") match {
            case Some(children: ModelNode) =>
              children map {
                case (childType: String, _: ModelNode) => childType
              }
            case None => Nil
          }
          logger.debug(s"Found ${childrenTypes.size} children types: $childrenTypes")

          // turn nested types into nested addresses
          val childrenAddresses = childrenTypes.map(childAddress(_, address))
          logger.debug(s"Addresses for the children types: $childrenAddresses")

          if (childrenAddresses.nonEmpty)
            childrenAddresses.foreach(address => {
              if (address.isDefined)
                attributesAndChildren(address.get, attributes)
            })

        case Response(Failure, error: ModelNode) =>
          logger.error(s"Error reading description @ $address: $error")
          // report an error as special DmrAttribute instance
          attributes.prepend(DmrAttribute.error(address, error))
      }
    }

    /** Create an address for the child type underneath the resource */
    def childAddress(childType: String, address: Address): Option[Address] = {

      val node = ModelNode() at address op 'read_children_names('child_type -> childType)
      logger.debug(s"Reading children names for type $childType")

      val result = client ! node map {
        case Response(Success, result: ModelNode) =>
          result match {
            case ModelNode(LIST) =>
              result.values match {
                // If there are child resource(s), use the first one for the address
                // TODO For some resources the set of attributes is different among the children
                case (headNode :: _) =>
                  val firstChild = headNode.asString.get
                  logger.debug(s"Found ${result.values.size} children: ${result.values} and taking the first one: $firstChild")
                  address / (childType -> firstChild)
                // otherwise try with "*" (which might not be supported)
                case Nil =>
                  logger.debug(s"Found no children -> using the wildcard operator")
                  address / (childType -> "*")
              }
          }
        case Response(Failure, error: ModelNode) =>
          logger.error(s"Error reading children names @ $address: $error")
          throw new Exception // will result in a Failure[Address] and is mapped to a None[Address] (see below)
      }
      result.toOption
    }

    // start recursion
    val attributesBuffer = ListBuffer[DmrAttribute]()
    attributesAndChildren(entryPoint, attributesBuffer)

    // and return all collected attributes
    attributesBuffer.toList
  }
}
