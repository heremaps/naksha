@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

@JsExport
open class JsonPatchRemove(): JsonPatchEntry() {
    companion object JsonPatchRemove_C {
        /**
         * The [PlatformType] of [JsonPatchRemove].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(JsonPatchRemove::class).withPackageName(PACKAGE_NAME)
    }

    @JsName("of")
    constructor(path: String): this() {
        setRaw("path", path)
        setRaw("op", "remove")
    }
}