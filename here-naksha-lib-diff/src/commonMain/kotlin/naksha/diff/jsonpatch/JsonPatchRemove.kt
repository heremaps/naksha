@file:Suppress("OPT_IN_USAGE")

package naksha.diff.jsonpatch

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
open class JsonPatchRemove(): JsonPatchEntry() {
    @JsName("of")
    constructor(path: String): this() {
        setRaw("path", path)
        setRaw("op", "remove")
    }
}