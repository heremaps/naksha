@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Int64
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.Version
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The PostgresQL transaction information object.
 * @since 3.0.0
 */
@JsExport
data class PgTxn(
    /**
     * The transaction-number.
     * @since 3.0.0
     */
    val number: Int64,

    /**
     * The Epoch timestamp _(milliseconds since 1 January 1970)_ when the transaction started.
     * @since 3.0.0
     */
    val epoch: Int64,

    /**
     * The version object, created from [number].
     */
    val version: Version = Version(number)
) {
    companion object PgTxn_C {
        /**
         * The [PlatformType] of [PgTxn].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgTxn::class).withPackageName(PACKAGE_NAME)
    }
}