@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

@JsExport
open class JsonPatchReplace(): JsonPatchEntry() {
    companion object JsonPatchReplace_C {
        /**
         * The [PlatformType] of [JsonPatchReplace].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(JsonPatchReplace::class).withPackageName(PACKAGE_NAME)
    }

    @JsName("of")
    constructor(path: String, value: Any?): this() {
        setRaw("path", path)
        setRaw("value", value)
        setRaw("op", "replace")
    }
}