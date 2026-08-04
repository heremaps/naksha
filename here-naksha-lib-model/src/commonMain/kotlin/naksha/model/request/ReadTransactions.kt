@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.Id
import naksha.base.Int64
import naksha.base.illegalState
import naksha.model.objects.NakshaDatabase
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureNumberMember
import naksha.model.request.ops.Equals
import naksha.model.request.ops.IsAnyOf
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Perform a read from the transaction collection to query for [transaction features][naksha.model.objects.NakshaTx].
 * @since 3.0
 */
@JsExport
open class ReadTransactions() : ReadFeatures() {

    /**
     * Read transactions from the given database.
     *
     * When no limits are set, this will read all transactions.
     * @param database the database to read from.
     * @since 3.0
     */
    @JsName("of")
    constructor(database: NakshaDatabase) : this() {
        databaseId = database.id
        catalogId = Id.ADMIN_CATALOG_ID
        collectionId = Id.TRANSACTIONS_COL_ID
    }

    /**
     * Read the version.
     * @param version the version to read.
     * @return this.
     * @since 3.0
     */
    fun readVersion(version: Int64): ReadTransactions {
        val v = version ushr 2
        val q = memberQuery
        if (q == null) {
            memberQuery = IsAnyOf(FeatureNumberMember.id, v)
            return this
        }
        if (q is IsAnyOf && q.at == FeatureNumberMember.id) {
            if (!q.items.contains(v)) q.items.add(v)
            return this
        }
        if (q is Equals && q.at == FeatureNumberMember.id) {
            val existing = q.value
            if (existing != v && existing != null) {
                memberQuery = IsAnyOf(FeatureNumberMember.id, existing, v)
                return this
            }
        }
        throw illegalState("Cannot find version query")
    }

    /**
     * Read the versions.
     * @param versions the versions to read.
     * @return this.
     * @since 3.0
     */
    fun readVersions(vararg versions: Int64): ReadTransactions {
        for (version in versions) readVersions(version)
        return this
    }
}
