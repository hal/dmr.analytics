package org.jboss.dmr.analytics

import org.jboss.dmr.analytics.AccessType.AccessType
import org.jboss.dmr.analytics.RestartPolicy.RestartPolicy
import org.jboss.dmr.analytics.Storage.Storage
import org.jboss.dmr.scala.{ComplexModelNode, ModelNode, ValueModelNode}
import org.jboss.dmr.{ModelType, ModelNode => JavaModelNode}

import scala.collection.JavaConversions._
import scala.util.Try

object MetaData {

  def parse(node: ModelNode, depth: Int): MetaData = {

    def parseValues(attribute: String): List[String] = {
      val nodes = node.get(attribute) flatMap (_.asList) getOrElse Nil
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
        case Some(stringValue) =>
          Try {
            valueToEnum(stringValue)
          } getOrElse defaultValue
        case None => defaultValue
      }
    }

    val description = node.get("description") flatMap (_.asString) getOrElse ""
    val allowNull = node.get("nillable") flatMap (_.asBoolean) getOrElse false
    val allowExpression = node.get("expressions-allowed") flatMap (_.asBoolean) getOrElse false
    val hasDefaultValue = node.get("default").isDefined
    val minLength = node.get("min-length") flatMap (_.asLong)
    val maxLength = node.get("max-length") flatMap (_.asLong)
    val allowedValues = parseValues("allowed")
    val alternatives = parseValues("alternatives")
    val requires = parseValues("requires")
    val valueTypeInfo = node.get("value-type") match {
      case Some(vtNode) =>
        vtNode match {
          case vmn: ValueModelNode =>
            val valueType = Try {
              ModelType.valueOf(vmn.asString.getOrElse(ModelType.UNDEFINED.name()))
            } getOrElse ModelType.UNDEFINED
            (valueType, 0)
          case _ => (ModelType.OBJECT, vtNode.depth)
        }
      case None => (ModelType.UNDEFINED, 0)
    }
    val accessType = parseEnum("access-type", AccessType.NA, AccessType.withName)
    val restartPolicy = parseEnum("restart-required", RestartPolicy.NA, RestartPolicy.withName)
    val storage = parseEnum("storage", Storage.NA, Storage.withName)
    val deprecated = node.get("deprecated").isDefined
    val alias = node.get("aliases") flatMap (_.asString)

    MetaData(description, depth, allowNull, allowExpression, minLength, maxLength, hasDefaultValue, allowedValues,
      alternatives, requires, valueTypeInfo._1, valueTypeInfo._2, accessType, restartPolicy, storage, deprecated, alias)
  }
}

/** Parsed version of an attributes meta data which roughly reflects [org.jboss.as.controller.AttributeDefinition] */
case class MetaData(description: String,
                    depth: Int = -1,
                    allowNull: Boolean = true,
                    allowExpression: Boolean = true,
                    minLength: Option[Long] = None,
                    maxLength: Option[Long] = None,
                    hasDefaultValue: Boolean = false,
                    allowedValues: List[String] = Nil,
                    alternatives: List[String] = Nil,
                    requires: List[String] = Nil,
                    valueType: ModelType = ModelType.UNDEFINED,
                    valueTypeDepth: Int = 0,
                    accessType: AccessType = AccessType.NA,
                    restartPolicy: RestartPolicy = RestartPolicy.NA,
                    storage: Storage = Storage.NA,
                    deprecated: Boolean = false,
                    alias: Option[String] = None)
