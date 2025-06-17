@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A small wrapper for a read-only PostgresQL connection. This is either a shared one (that must not be closed) or an individual read-only connection, which must be closed after usage.
 * @since 3.0
 */
@JsExport
class PgSessionReadConn internal constructor(val conn: PgConnection, private var closeUnderlying: Boolean) : AutoCloseable {
    companion object PgSessionReadConn_C {
        /**
         * The [PlatformType] of [PgSessionReadConn].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgSessionReadConn::class).withPackageName(PACKAGE_NAME)
    }

    override fun close() {
        if (closeUnderlying) conn.close()
    }
}