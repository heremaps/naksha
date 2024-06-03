package com.here.naksha.lib.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix

object JvmAuthParser : AuthJsonParser {

    private val MAPPER = ObjectMapper()
    override fun parseUrm(json: String): UserRightsMatrix {
        TODO()
//        val rawArm = parseRawAuthMatrix(json)
//        return Base.assign(rawArm, UserRightsMatrix.klass)
    }

    override fun parseArm(json: String): AccessRightsMatrix {
        TODO()
//        val rawArm = parseRawAuthMatrix(json)
//        return Base.assign(rawArm, AccessRightsMatrix.klass)
    }
//
//    private fun parseRawAuthMatrix(json: String): BaseObject {
//        val keysAndValues = mutableListOf<Any?>()
//        val root = MAPPER.readTree(json)
//        root.fields()
//            .asSequence()
//            .forEach { (serviceName, serviceNode) ->
//                keysAndValues.add(serviceName)
//                keysAndValues.add(parseService(serviceNode))
//            }
//        return BaseObject(*keysAndValues.toTypedArray())
//    }
//
//    private fun parseService(serviceNode: JsonNode): BaseObject {
//        val keysAndValues = mutableListOf<Any?>()
//        serviceNode.fields()
//            .asSequence()
//            .forEach { (actionName, actionNode) ->
//                keysAndValues.add(actionName)
//                keysAndValues.add(parseActions(actionNode))
//            }
//        return BaseObject(*keysAndValues.toTypedArray())
//    }
//
//    private fun parseActions(actionNode: JsonNode): BaseArray<BaseObject> {
//        require(actionNode.isArray) { "Expected action array" }
//        val attributeMaps = actionNode
//            .map { attributeMap -> parseAttributeMap(attributeMap) }
//            .toTypedArray()
//        return BaseArray(*attributeMaps)
//    }
//
//    private fun parseAttributeMap(attributeMapNode: JsonNode): BaseObject {
//        val keysAndValues = mutableListOf<Any?>()
//        attributeMapNode.fields()
//            .forEach { (attrKey, attrValue) ->
//                keysAndValues.add(attrKey)
//                if (attrValue.isTextual) {
//                    keysAndValues.add(attrValue.textValue())
//                } else if (attrValue.isArray) {
//                    attrValue.map { it.textValue() }
//                        .toTypedArray()
//                        .let { keysAndValues.add(Base.newArray(*it)) }
//                } else {
//                    throw IllegalArgumentException("Invalid entry for attribute map key: '$attrKey' - expected textual value / array")
//                }
//            }
//        return BaseObject(*keysAndValues.toTypedArray())
//    }
}
