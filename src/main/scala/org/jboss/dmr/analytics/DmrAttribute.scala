package org.jboss.dmr.analytics

import org.jboss.dmr.scala.{Address, ModelNode}

/**
 * Simple case class for the attribute type. Makes it easier to work with the types in Scala
 */
case class AttributeType(`type`: String)

/**
 * Case class describing one attribute of a resource in the management model.
 * The DMR attribute is the atomic unit of information used as the basis for all Spark related analytics.
 */
case class DmrAttribute(name: String, `type`: AttributeType, address: Address, metaData: ModelNode)

object DmrAttribute {
  def error(address: Address, node: ModelNode) = DmrAttribute("ERROR", AttributeType("ERROR"), address, node)
}
