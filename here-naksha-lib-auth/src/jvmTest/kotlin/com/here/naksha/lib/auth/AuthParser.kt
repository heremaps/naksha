package com.here.naksha.lib.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix
import naksha.base.P_List
import naksha.base.P_Object
import naksha.base.Proxy.Companion.box

object AuthParser {

    private val MAPPER = ObjectMapper()
    fun parseUrm(json: String): UserRightsMatrix {
        val rootNode = MAPPER.readTree(json)
        return parseUrm(rootNode)
    }

    fun parseArm(json: String): AccessRightsMatrix {
        val rootNode = MAPPER.readTree(json)
        return parseArm(rootNode)
    }

    fun parseUrm(rootNode: JsonNode): UserRightsMatrix {
        val rawArm = parseMatrix(rootNode)
        return box(rawArm, UserRightsMatrix::class)!!
    }

    fun parseArm(rootNode: JsonNode): AccessRightsMatrix {
        val rawArm = parseMatrix(rootNode)
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
    private fun parseMatrix(matrixNode: JsonNode): P_Object {
        val rootMatrix = P_Object()
        matrixNode.fields()
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
        val actions = object : P_List<P_Object>(P_Object::class) {}
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
                    val values = object : P_List<String>(String::class) {}
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
