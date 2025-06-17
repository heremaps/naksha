@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [PostgresQL instances configurations][PgInstanceConfig].
 * @since 3.0.0
 */
@JsExport
class PgInstanceConfigList : ListProxy<PgInstanceConfig>(PgInstanceConfig.TYPE) {
    companion object PgInstanceConfigList_C {
        /**
         * The [PlatformType] of [PgInstanceConfigList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgInstanceConfigList::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Add a configuration based upon the given URI.
     * @param uri the URI to parse, formatted like `jdbc:postgresql://{host}[:{port}]/{db}?user={user}&password={password}`.
     * @since 3.0
     */
    fun addUri(uri: String): PgInstanceConfigList {
        add(PgInstanceConfig.fromUri(uri))
        return this
    }
}

// TODO: Add helper method to add an instance config by URL like:
//       addByUrl("jdbc:...")
