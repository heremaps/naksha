package naksha.base

import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

class FromJsonOptions(val parseDataUrl: Boolean = false) {
    @OptIn(ExperimentalJsStatic::class)
    companion object {
        @JvmStatic
        @JsStatic
        val DEFAULT = FromJsonOptions()
    }
}