@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A mapper that allows reading a feature. After mapping the [reader] can be used to access the content of the feature.
 */
@JsExport
open class JbFeature : JbStruct<JbFeature>() {
    private var id: String? = null
    private var featureType: Int = -1

    override fun clear(): JbFeature {
        super.clear()
        id = null
        featureType = -1
        return this
    }

    override fun parseHeader() {
        check(unitType == TYPE_FEATURE) { "Mapped structure is no feature, but ${JbReader.unitTypeName(unitType)}" }
        // The id of global dictionary (optional).
        if (reader.isString()) {
            val globalDictId = reader.readString()
            reader.globalDict = Jb.env.getGlobalDictionary(globalDictId)
            check(reader.globalDict != null) { "Unable to load necessary global dictionary '$globalDictId'" }
        } else {
            check(reader.isNull()) { "Expected global dictionary ID to be either a string or null, but found ${JbReader.unitTypeName(reader.unitType())}" }
        }
        check(reader.nextUnit()) { "Failed to seek forward to feature-id field" }
        // The feature-id (optional).
        if (reader.isString()) {
            id = reader.readString()
        } else {
            check(reader.isNull()) { "Expected feature-id to be either a string or null, but found ${JbReader.unitTypeName(reader.unitType())}" }
        }
        check(reader.nextUnit()) { "Failed to seek forward to local dictionary field" }
        // The embedded local dictionary.
        check(reader.isDictionary()) { "Expect local dictionary, but found ${JbReader.unitTypeName(reader.unitType())}" }
        reader.localDict = JbDict().mapReader(reader)
        check(reader.nextUnit()) { "Failed to seek forward to the feature payload" }
        featureType = reader.unitType()
    }

    /**
     * Returns the **id** of the feature, if any is encoded.
     * @return The (optional) **id** of the feature.
     */
    fun id(): String? {
        return id
    }

    /**
     * Returns the feature type.
     * @return The feature type.
     */
    fun featureType(): Int {
        return featureType
    }
}