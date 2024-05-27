@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class P_NakshaDelta(vararg args:Any?) : P_Object(*args) {
    companion object {

        @JvmStatic
        val REVIEW_STATE = Platform.intern("reviewState")

        @JvmStatic
        val CHANGE_STATE = Platform.intern("changeState")
    }

    // TODO implement review state
}