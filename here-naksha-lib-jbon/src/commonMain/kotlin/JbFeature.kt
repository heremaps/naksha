@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A mapper that allows reading a feature. After mapping the [reader] can be used to access the content of the feature.
 */
@JsExport
open class JbFeature : JbStructMapper<JbFeature>() {
    private var id: String? = null
    private var featureType: Int = -1

    override fun clear(): JbFeature {
        super.clear()
        id = null
        featureType = -1
        return this
    }

    override fun parseHeader(mandatory: Boolean) {
        // Header parsing is always mandatory for features!
        check(reader.unitType() == TYPE_FEATURE)
        val unitSize = reader.unitSize()
        check(reader.enterUnit())
        // The id of global dictionary (optional).
        if (reader.isString()) {
            val globalDictId = reader.readString()
            reader.globalDict = Jb.env.getGlobalDictionary(globalDictId)
            check(reader.globalDict != null)
        } else {
            check(reader.isNull())
        }
        check(reader.nextUnit())
        // The feature-id (optional).
        if (reader.isString()) {
            id = reader.readString()
        } else if (reader.isText()) {
            id = reader.readText()
        } else {
            check(reader.isNull())
        }
        check(reader.nextUnit())
        // The embedded local dictionary.
        check(reader.isLocalDict())
        reader.localDict = JbDict().mapReader(reader)
        check(reader.nextUnit())
        featureType = reader.unitType()
        // Content.
        setContentSize(unitSize)
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