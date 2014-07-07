package org.jboss.dmr.analytics

import org.jboss.dmr.analytics.AccessType.AccessType
import org.jboss.dmr.analytics.RestartPolicy.RestartPolicy
import org.jboss.dmr.analytics.Storage.Storage
import org.jboss.dmr.scala.ModelNode

object MetaData {

  def parse(node: ModelNode, depth: Int): MetaData = {

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

    MetaData(description, depth, allowNull, allowExpression, hasDefaultValue, allowedValues, alternatives, requires,
      accessType, restartPolicy, storage, deprecated, alias)
  }
}

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
