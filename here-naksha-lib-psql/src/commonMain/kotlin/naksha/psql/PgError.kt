@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * An error as reported by PostgresQL.
 * @since 3.0.0
 */
@JsExport
data class PgError(
    /**
     * The last [PostgreSQL Error Code](https://www.postgresql.org/docs/current/errcodes-appendix.html) or _null_, if no error has happened.
     */
    val errNo: String,

    /**
     * The human-readable error message.
     */
    val errMsg: String
) {
    companion object PgError_C {
        /**
         * The [PlatformType] of [PgError].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgError::class).withPackageName(PACKAGE_NAME)
    }
}