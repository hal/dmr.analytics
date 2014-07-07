package org.jboss.dmr.analytics

import org.jboss.dmr.ModelType
import org.jboss.dmr.scala.Address

/**
 * Case class describing one attribute of a resource in the management model.
 * The DMR attribute is the atomic unit of information used as the basis for all Spark related analytics.
 */
case class DmrAttribute(name: String, `type`: ModelType, address: Address, metaData: MetaData)

object DmrAttribute {
  def error(description: String, address: Address) = DmrAttribute("ERROR", ModelType.UNDEFINED, address, MetaData(description))
}
