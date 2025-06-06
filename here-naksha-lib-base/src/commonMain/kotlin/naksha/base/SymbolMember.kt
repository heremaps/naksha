@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A symbol member is a hidden property, supported by all [PlatformObject]'s, that can be created, modified, and deleted at runtime. This object is just a read-only representation.
 * @property self The [PlatformObject] to which this [SymbolMember] is linked.
 * @property symbol The [Symbol] that is used to link the [value].
 * @property value The value being linked, normally a [Proxy].
 * @since 3.0
 * @see [Symbol]
 */
@JsExport
data class SymbolMember(val self: PlatformObject, val symbol: Symbol, val value: Any?) {
    companion object SymbolMemberCompanion {
        /**
         * The [PlatformType] of [SymbolMember].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<SymbolMember> = forKClass(SymbolMember::class).withPackageName(PACKAGE_NAME)
    }
}
