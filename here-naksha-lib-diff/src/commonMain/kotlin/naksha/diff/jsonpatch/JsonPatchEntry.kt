@file:Suppress("OPT_IN_USAGE")

package naksha.diff.jsonpatch

import naksha.base.PAnyMap
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
open class JsonPatchEntry(): PAnyMap() {

    @JsName("of")
    constructor(op: String, path: String): this() {
        setRaw("op", op)
        setRaw("path", path)
    }
}