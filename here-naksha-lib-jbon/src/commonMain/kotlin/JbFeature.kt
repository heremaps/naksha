@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A mapper that allows reading a feature.
 */
@JsExport
open class JbFeature : JbObjectMapper<JbFeature>() {
    private var id: String? = null
    private var featureType: Int = -1

    override fun clear(): JbFeature {
        super.clear()
        id = null
        featureType = -1
        localDict = null
        globalDict = null
        return this
    }

    override fun parseHeader(mandatory: Boolean) {
        check(type() == TYPE_FEATURE)
        // Total size of feature.
        addOffset(1)
        check(isInt())
        val size = readInt32()
        check(next())
        // The feature-id (optional).
        if (isString()) {
            id = readString().toString()
        } else {
            check(isNull())
        }
        check(next())
        // The id of global dictionary (optional).
        if (isString()) {
            val globalDictId = readString()
            globalDict = JbPlatform.get().getGlobalDictionary(globalDictId)
            check(globalDict != null)
        } else {
            check(isNull())
        }
        check(next())
        // The embedded local dictionary.
        check(isLocalDict())
        localDict = JbDict().mapView(view, offset())
        check(next())
        featureType = type()
        // Content.
        setContentSize(size)
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