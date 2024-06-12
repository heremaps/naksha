package com.here.naksha.lib.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix
import naksha.base.*
import naksha.base.Proxy.Companion.box

object AuthParser {

    private val MAPPER = ObjectMapper()
    fun parseUrm(json: String): UserRightsMatrix {
        val rawArm = parseRawAuthMatrix(json)
        return box(rawArm, UserRightsMatrix::class)!!
    }

    fun parseArm(json: String): AccessRightsMatrix {
        val rawArm = parseRawAuthMatrix(json)
        return box(rawArm, AccessRightsMatrix::class)!!
    }

    /*
    raw type structure for both Matrix types:

    P_Map< // root matrix
        String,  // service name
        P_Map<   // service
            String,   // action name
            P_List<   // action's attribute maps
                P_Object   // attribute map
            >
        >
     >
     */
    private fun parseRawAuthMatrix(json: String): P_Object {
        val rootMatrix = P_Object()
        MAPPER.readTree(json)
            .fields()
            .asSequence()
            .forEach { (serviceName, serviceNode) ->
                rootMatrix[serviceName] = parseService(serviceNode)
            }
        return rootMatrix
    }

    private fun parseService(serviceNode: JsonNode): P_Object {
        val service = P_Object()
        serviceNode.fields()
            .asSequence()
            .forEach { (actionName, actionNode) ->
                service[actionName] = parseActions(actionNode)
            }
        return service
    }

    private fun parseActions(actionNode: JsonNode): P_List<P_Object> {
        require(actionNode.isArray) { "Expected action array" }
        val actions = object: P_List<P_Object>(P_Object::class){}
        actionNode.forEach { actions.add(parseAttributeMap(it)) }
        return actions
    }

    private fun parseAttributeMap(attributeMapNode: JsonNode): P_Object {
        val keysAndValues = mutableListOf<Any?>()
        val attributeMap = P_Object()
        attributeMapNode.fields()
            .forEach { (attrKey, attrValue) ->
                keysAndValues.add(attrKey)
                if (attrValue.isTextual) {
                    attributeMap[attrKey] = attrValue.textValue()
                } else if (attrValue.isArray) {
                    val values = object: P_List<String>(String::class){}
                    attrValue.map { it.textValue() }
                        .toTypedArray()
                        .let { values.addAll(it) }
                    attributeMap[attrKey] = values
                } else {
                    throw IllegalArgumentException("Invalid entry for attribute map key: '$attrKey' - expected textual value / array")
                }
            }
        return attributeMap
    }
}
