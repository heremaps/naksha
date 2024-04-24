package com.here.naksha.lib.nak

import kotlin.js.JsExport

/**
 * A simple wrapper for key-value pairs.
 * @property key The key.
 * @property value The value.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class NakPair<K, V>(var key: K, var value: V) {
    /**
     * Creates a clone of this Naksha key-value pair. Note, the clone is not recursive.
     * @return The clone.
     */
    fun copy(): NakPair<K, V> = NakPair(key, value)
}
