package org.jboss.dmr.analytics

import org.jboss.dmr.ModelType
import org.jboss.dmr.analytics.AccessType.AccessType
import org.jboss.dmr.analytics.RestartPolicy.RestartPolicy
import org.jboss.dmr.analytics.Storage.Storage
import org.jboss.dmr.scala.Address

/** Parsed version of an attributes meta data which roughly reflects [org.jboss.as.controller.AttributeDefinition] */
case class MetaData(description: String,
                    depth: Int = -1,
                    allowNull: Boolean = true,
                    allowExpression: Boolean = true,
                    hasDefaultValue: Boolean = false,
                    allowedValues: List[String] = Nil,
                    alternatives: List[String] = Nil,
                    requires: List[String] = Nil,
                    accessTyp: AccessType = AccessType.UNKNOWN,
                    restartPolicy: RestartPolicy = RestartPolicy.UNKNOWN,
                    storage: Storage = Storage.UNKNOWN,
                    deprecated: Boolean = false,
                    alias: Option[String] = None)

/**
 * Case class describing one attribute of a resource in the management model.
 * The DMR attribute is the atomic unit of information used as the basis for all Spark related analytics.
 */
case class DmrAttribute(name: String, `type`: ModelType, address: Address, metaData: MetaData)

object DmrAttribute {
  def error(description: String, address: Address) = DmrAttribute("ERROR", ModelType.UNDEFINED, address, MetaData(description))
}
