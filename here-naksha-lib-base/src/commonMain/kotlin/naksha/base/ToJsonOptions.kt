package naksha.base

import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

class ToJsonOptions(val int64Encoding: String? = null) {
    @OptIn(ExperimentalJsStatic::class)
    companion object {
        @JvmStatic
        @JsStatic
        val DEFAULT = ToJsonOptions()
    }
}