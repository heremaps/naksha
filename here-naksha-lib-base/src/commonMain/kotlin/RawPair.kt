@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A simple wrapper for key-value pairs.
 * @property key The key.
 * @property value The value.
 */
@JsExport
class RawPair<K, V>(var key: K, var value: V) : Raw() {
    /**
     * Creates a clone of this Naksha key-value pair. Note, the clone is not recursive.
     * @return The clone.
     */
    fun copy(): RawPair<K, V> = RawPair(key, value)

    override fun toPlatform(): PObject = Base.newObject("key", key, "value", value)
}
