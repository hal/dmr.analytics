package org.jboss.dmr.analytics

import java.util

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
     * Reads the attributes and nested types of the specified address.
     * Prepends the attributes to the specified list.
     */
    def attributesAndChildren(address: Address, level: Int, attributes: ListBuffer[DmrAttribute]) {
      val node = ModelNode() at address op 'read_resource_description
      logger.debug(s"Read resource description for $address")

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
                  val typeValue = metaData.get("type").flatMap(_.asString)
                  val modelType = ModelType.valueOf(typeValue.getOrElse("UNDEFINED"))
                  DmrAttribute(attributeName, modelType, address, parseMetaData(metaData))
              }
            case None =>
              logger.warn(s"No attributes found for read-resource-description @ $address")
              Nil
          }
          attributes.prependAll(dmrAttributes)
          logger.debug(s"Added ${dmrAttributes.size} attributes. New size: ${attributes.size}")

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

          // read next resource recursively
          childrenAddresses.foreach(address => {
            if (address.isDefined)
              attributesAndChildren(address.get, level + 1, attributes)
          })

        case Response(Failure, failure: ModelNode) =>
          val error = failure.asString getOrElse "undefined"
          logger.error(s"Error reading description @ $address: $error")
          // report an error as special DmrAttribute instance
          attributes.prepend(DmrAttribute.error(error, address))
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

    def parseMetaData(node: ModelNode): MetaData = {

      def parseValues(attribute: String): List[String] = {
        val nodes = node.get(attribute) flatMap(_.asList) getOrElse Nil
        val values = for {
          node <- nodes
          value <- node.asString
        } yield value
        values
      }

      def parseEnum[E](attribute: String, defaultValue: E, valueToEnum: (String) => E) = {
        val value = for {
          value <- node.get(attribute)
          stringValue <- value.asString
        } yield stringValue

        value match {
          case Some(enumLiteral) => valueToEnum(enumLiteral)
          case None => defaultValue
        }
      }

      val description = node.get("description") flatMap (_.asString) getOrElse ""
      val allowNull = node.get("nillable") flatMap (_.asBoolean) getOrElse false
      val allowExpression = node.get("expressions-allowed") flatMap (_.asBoolean) getOrElse false
      val hasDefaultValue = node.get("default").isDefined
      val allowedValues = parseValues("allowed")
      val alternatives = parseValues("alternatives")
      val requires = parseValues("requires")

      val accessType = parseEnum("access", AccessType.UNKNOWN, AccessType.withName)
      val restartPolicy = parseEnum("restart-required", RestartPolicy.UNKNOWN, RestartPolicy.withName)
      val storage = parseEnum("storage", Storage.UNKNOWN, Storage.withName)
      val deprecated = node.get("deprecated").isDefined
      val alias = node.get("alias") flatMap (_.asString)

      MetaData(description, allowNull, allowExpression, hasDefaultValue, allowedValues, alternatives, requires,
        accessType, restartPolicy, storage, deprecated, alias)
    }

    // start recursion
    val attributesBuffer = ListBuffer[DmrAttribute]()
    attributesAndChildren(entryPoint, 0, attributesBuffer)

    // and return all collected attributes
    attributesBuffer.toList
  }
}
