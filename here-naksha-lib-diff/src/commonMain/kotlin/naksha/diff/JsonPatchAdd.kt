@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

@JsExport
open class JsonPatchAdd(): JsonPatchEntry() {
    companion object JsonPatchAdd_C {
        /**
         * The [PlatformType] of [JsonPatchAdd].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(JsonPatchAdd::class).withPackageName(PACKAGE_NAME)
    }

    @JsName("of")
    constructor(path: String, value: Any?): this() {
        setRaw("path", path)
        setRaw("value", value)
        setRaw("op", "add")
    }
}