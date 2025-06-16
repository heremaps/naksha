@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.AnyObject
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

@JsExport
open class JsonPatchEntry(): AnyObject() {
    companion object JsonPatchEntry_C {
        /**
         * The [PlatformType] of [JsonPatchEntry].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(JsonPatchEntry::class).withPackageName(PACKAGE_NAME)
    }

    @JsName("of")
    constructor(op: String, path: String): this() {
        setRaw("op", op)
        setRaw("path", path)
    }
}