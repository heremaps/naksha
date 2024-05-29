@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The Naksha type for an object.
 */
@JsExport
open class P_Object() : P() {

    /**
     * Create an object from a list of entries.
     * @param entries The entries to copy into this object.
     */
    @JsName("of")
    constructor(vararg entries: P_Entry<String, Any?>) : this() {
        bind(N.newObject(*entries), N.symbolOf(this::class))
    }

    override fun createData(): N_Object = N.newObject()
}