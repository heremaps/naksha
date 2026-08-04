@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.PTypedArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A list of [PostgresQL instances configurations][PgInstanceConfig].
 * @since 3.0.0
 */
@JsExport
class PgInstanceConfigList : PTypedArray<PgInstanceConfig>(PgInstanceConfig::class) {

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
