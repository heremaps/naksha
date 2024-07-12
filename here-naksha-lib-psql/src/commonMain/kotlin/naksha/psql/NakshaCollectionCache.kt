package naksha.psql

import naksha.model.Row

/**
 * A cached collection. Internally we should only pass around this.
 * @property storage the storage in which the collection is held.
 * @property row the collection as read from the database table.
 * @property reader the JBON reader of the [Row.feature].
 * @property estimatedFeatureCount the cached amount of features in HEAD table; `-1` while not yet been read.
 * @property estimatedDeletedCount the cached amount of deleted features in DEL table; `-1` while not yet been read.
 */
internal data class NakshaCollectionCache(
    val storage: PgStorage,
    val row: Row,
    val reader: JbNakshaCollection,
    var estimatedFeatureCount: Int = -1,
    var estimatedDeletedCount: Int = -1
)