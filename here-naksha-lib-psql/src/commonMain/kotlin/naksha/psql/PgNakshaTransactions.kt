@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha.NakshaCompanion.ADMIN_CATALOG_ID
import naksha.model.Naksha.NakshaCompanion.TRANSACTIONS_COL_ID
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardIndices
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import kotlin.js.JsExport

/**
 * The internal collection in the admin-catalog, that keeps track of the transactions of the storage.
 *
 * This is a standard partitioned collection with:
 * - 16 HEAD partitions (partitioned by `fn`)
 * - history enabled (`storeHistory = ON`), partitioned yearly via `$hst$<year>` naming
 * - deleted and meta storage disabled
 * - a `txn_unique` index on `version` for fast transaction lookups
 * - [StandardIndices.PublishNumber] (`pn`) — BTREE index for efficient visibility-ordered scans
 * - [StandardIndices.PublishTime] (`pt`) — BTREE index for time-range scans by publisher timestamp
 * - [StandardIndices.GlobalVersion] (`gv`) — BTREE index for HERE global version scans
 *
 * The [StandardMembers.PublishNumber], [StandardMembers.PublishTime], and
 * [StandardMembers.GlobalVersion] members are all `null` until an external publisher or
 * HERE global sequencer populates them.
 */
@JsExport
class PgNakshaTransactions internal constructor(adminCatalog: PgAdminCatalog) : PgCollection(adminCatalog, NakshaCollection()
    .withCatalogId(ADMIN_CATALOG_ID)
    .withId(TRANSACTIONS_COL_ID)
    .withStoreDeleted(StoreMode.OFF)
    .withStoreHistory(StoreMode.ON)
    .withStoreMeta(StoreMode.OFF)
    .withPartitions(16)
    .withMembers(
        StandardMembers.PublishNumber,
        StandardMembers.PublishTime,
        StandardMembers.GlobalVersion,
    )
    .withIndices(
        StandardIndices.PublishNumber,
        StandardIndices.PublishTime,
        StandardIndices.GlobalVersion,
    )
), PgInternalCollection

// TODO: We need to fix this, we want all internal collections to use XYZ members and indices
//       For this case, we additionally want the publication members and indices.