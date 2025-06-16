@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [checks][Check].
 * @since 3.0
 */
@JsExport
class CheckList() : ListProxy<Check>(Check.TYPE) {

    @JsName("of")
    constructor(vararg checks: Check) : this() {
        addAll(checks)
    }

    companion object CheckList_C {
        /**
         * The [PlatformType] of [CheckList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<CheckList> = forKClass(CheckList::class).withPackageName(PACKAGE_NAME)
    }
}