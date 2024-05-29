@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A platform iterator.
 * @property done Set to _false_ if the iterator has no more values; _true_ if this is the end ([value] will be _undefined_).
 * @property value The value or _undefined_, if [done] is _false_.
 */
@JsExport
abstract class N_Iterator<VALUE>(var done: Boolean, var value: VALUE?) {
    /**
     * Loads the next value and returns either this iterator again, with next value loaded, or a new iterator instance with the next value.
     * @return The iterator with the next value, can be _this_ or a new instance.
     */
    abstract fun next(): N_Iterator<VALUE>
}