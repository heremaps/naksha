@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A META table.
 * @since 3.0
 * @see [PgTable]
 */
@JsExport
class PgMeta(val head: PgHead) : PgTable(head.collection, "${head.collection.id}${PG_META}", head.storageClass, true) {
    companion object PgMeta_C {
        /**
         * The [PlatformType] of [PgMeta].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgMeta::class).withPackageName(PACKAGE_NAME)
    }
}
