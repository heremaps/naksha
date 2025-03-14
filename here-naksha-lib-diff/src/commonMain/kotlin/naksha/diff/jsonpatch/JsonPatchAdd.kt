@file:Suppress("OPT_IN_USAGE")

package naksha.diff.jsonpatch

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
open class JsonPatchAdd(): JsonPatchEntry() {
    @JsName("of")
    constructor(path: String, value: Any?): this() {
        setRaw("path", path)
        setRaw("value", value)
        setRaw("op", "add")
    }
}