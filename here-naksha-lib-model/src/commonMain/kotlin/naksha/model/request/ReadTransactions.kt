@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ADMIN_CATALOG_TEXT
import naksha.base.TRANSACTIONS_COL_TEXT
import naksha.base.Version
import naksha.base.illegalState
import naksha.model.objects.StandardMembers
import naksha.model.request.ops.IsAnyOf
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Perform a read from the transaction log to query for [transaction features][naksha.model.objects.NakshaTx].
 * @since 3.0
 */
@JsExport
open class ReadTransactions: ReadFeatures() {
    init {
        catalogId = ADMIN_CATALOG_TEXT
        collectionId = TRANSACTIONS_COL_TEXT
    }

    /**
     * Read the version.
     * @param version the version to read.
     * @return this.
     * @since 3.0
     */
    fun readVersion(version: Version): ReadTransactions {
        addAll( Array(1) { version.number or 3L })
        return this
    }

    /**
     * Read the version.
     * @param version the version to read.
     * @return this.
     * @since 3.0
     */
    @JsName("readVersionInt64")
    fun readVersion(version: Long): ReadTransactions {
        addAll( Array(1) { version or 3L })
        return this
    }

    /**
     * Read the versions.
     * @param versions the versions to read.
     * @return this.
     * @since 3.0
     */
    fun readVersions(vararg versions: Version): ReadTransactions {
        addAll( Array(versions.size) { versions[it].number or 3L })
        return this
    }

    /**
     * Read the versions.
     * @param versions the versions to read.
     * @return this.
     * @since 3.0
     */
    @JsName("readVersionsInt64")
    fun readVersions(vararg versions: Long): ReadTransactions {
        addAll(Array(versions.size) { versions[it] or 3L })
        return this
    }

    /**
     * @param versions an array of versions with the lowest two bit already set.
     */
    private fun addAll(versions: Array<Long>) {
        val query = this.queryMembers
        if (query is IsAnyOf) {
            if (query.at != StandardMembers.FeatureNumber.name) {
                throw illegalState("ReadTransactions requires a single IsAnyOf query against feature-number")
            }
            // All versions must have the lowest 2-bit set.
            for (version in versions) query.items.add(version)
        } else if (query == null) {
            // All versions must have the lowest 2-bit set.
            queryMembers = IsAnyOf(StandardMembers.FeatureNumber, *versions)
        } else throw illegalState("ReadTransactions requires a single IsAnyOf query against feature-number")
    }
}
