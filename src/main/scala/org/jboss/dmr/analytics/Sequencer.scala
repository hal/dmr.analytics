package org.jboss.dmr.analytics

import org.jboss.dmr.ModelType.{LIST, OBJECT}
import org.jboss.dmr.repl.Response._
import org.jboss.dmr.repl.{Client, Response}
import org.jboss.dmr.scala._

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
 * Enters the management model at the specified entry point (or at the root if no entry point is given),
 * iterates over all attributes of all resources and turns them into a list of [[DmrAttribute]].
 */
class Sequencer(client: Client) {

  def read(entryPoint: Address = root): List[DmrAttribute] = {

    /**
     * Reads the attributes and nested types of the specified address.
     * Prepends the attributes to the specified list.
     */
    def attributesAndChildren(address: Address, allAttributes: ListBuffer[DmrAttribute]): Unit = {
      val node = ModelNode() at address op 'read_resource_description

      client ! node map {
        case Response(Success, result) =>
          // the result comes in two flavours:
          //   - simple model node for none-wildcard rrd operations
          //   - list model node for wildcard rrd operations
          val commonResult = result match {
            case ModelNode(OBJECT) => result
            case ModelNode(LIST) => result.values.headOption match {
              case Some(head) => head.getOrElse("result", ModelNode.Undefined)
              case None => ModelNode.Undefined
            }
            case _ => ModelNode.Undefined
          }

          // if there are attributes, read the key value tuples and turn them into a collection of DmrAttribute
          val dmrAttributes = commonResult.get("attributes") match {
            case Some(levelAttributes: ModelNode) =>
              levelAttributes map {
                case (attributeName: String, metaData: ModelNode) =>
                  DmrAttribute(attributeName,
                    AttributeType(metaData("type").asString.getOrElse("UNDEFINED")), address, metaData)
              }
            case None => Nil
          }
          allAttributes.prepend(dmrAttributes.toList: _*)

          // read nested types
          val childTypes = commonResult.get("children") match {
            case Some(children) => children map {
              case (childType, _) => childType
            }
            case None => Nil
          }

          // turn nested types into nested addresses
          val childAddresses = childTypes.map(childAddress(_, address))

          if (childAddresses.nonEmpty)
            childAddresses.foreach(address => {
              attributesAndChildren(address, allAttributes)
            })

        // report an error as special DmrAttribute instance
        case Response(Failure, error) => allAttributes.prepend(DmrAttribute.error(address, error))
      }
    }

    /** Create an address for the child type underneath the resource */
    def childAddress(childType: String, resource: Address) = {

      val node = ModelNode() at resource op 'read_children_names('child_type -> childType)

      val clientResult = client ! node map {
        case Response(Success, result) => result match {
          case ModelNode(LIST) => result.values match {
            // If there are child resource(s), use the first one for the address
            // TODO There might be differences in the number of attributes between the siblings!
            case (headNode :: _) => resource / (childType -> headNode.asString.get)
            // otherwise try with "*" (which might not be supported)
            case Nil => resource / (childType -> "*")
          }
        }
      }
      clientResult.get
    }

    // start recursion
    val collectedAttributes = ListBuffer[DmrAttribute]()
    attributesAndChildren(entryPoint, collectedAttributes)
    return collectedAttributes.toList
  }
}
