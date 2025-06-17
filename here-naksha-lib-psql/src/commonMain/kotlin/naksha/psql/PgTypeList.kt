@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [PgType's][PgType].
 * @since 3.0
 */
@JsExport
class PgTypeList : ListProxy<PgType>(PgType.TYPE) {
    companion object PgTypeList_C {
        /**
         * The [PlatformType] of [PgTypeList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgTypeList::class).withPackageName(PACKAGE_NAME)
    }
}