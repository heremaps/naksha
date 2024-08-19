package naksha.psql.executors

import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.psql.*
import kotlin.jvm.JvmField

open class RowUpdater(
    /**
     * The session to which this writer is linked.
     */
    @JvmField val session: PgSession
) {

    /**
     * Create the metadata for an _INSERT_ operation.
     * @param collection the collection into which to insert.
     * @param feature the feature to insert.
     * @return the metadata for this operation.
     */
    fun xyzInsert(collection: PgCollection, feature: NakshaFeature): Metadata {
        val version = session.version()
        val versionTime = session.versionTime()
//        return Metadata(
//            createdAt = versionTime,
//            updatedAt = versionTime,
//            authorTs = versionTime,
//            version = version.txn,
//            uid = session.uid.getAndAdd(1),
//            hash = Metadata.hash(feature, session.options.excludePaths, session.options.excludeFn),
//            geoGrid = Metadata.geoGrid(feature),
//            flags = flags(collection.nakshaCollection),
//            appId = session.options.appId,
//            author = session.options.actor,
//            type = featureType(collection, feature),
//            origin = feature.properties.xyz.origin,
//            id = feature.id
//        )
        TODO("Finish me!")
    }

    /**
     * Create the metadata for an _INSERT_ operation.
     * @param collection the collection into which to insert.
     * @param feature the feature to insert.
     * @return the metadata for this operation.
     */
    fun xyzUpdate(collection: PgCollection, feature: NakshaFeature, OLD: Tuple): Metadata {
        if (OLD.meta.nextVersion != null) {
            throw NakshaException(ILLEGAL_STATE, "The OLD row must not have a 'nextVersion'",
                id = feature.id
            )
        }
        if (OLD.meta.id != feature.id) {
            throw NakshaException(
                ILLEGAL_STATE,
                "The OLD row must have the same id, but differs: '${feature.id} != '${OLD.meta.id}",
                id = feature.id
            )
        }
        val version = session.version()
        val versionTime = session.versionTime()
        OLD.meta.nextVersion = version.txn
        return OLD.meta.copy(
            updatedAt = versionTime,
            authorTs = if (session.options.author == null) OLD.meta.authorTs else versionTime,
            version = version.txn,
            prevVersion = OLD.meta.version,
            uid = session.uid.getAndAdd(1),
            puid = OLD.meta.uid,
            hash = Metadata.hash(feature, session.options.excludePaths, session.options.excludeFn),
            changeCount = OLD.meta.changeCount + 1,
            geoGrid = Metadata.geoGrid(feature),
            flags = flags(collection.nakshaCollection),
            appId = session.options.appId,
            author = session.options.author ?: OLD.meta.author,
            type = featureType(collection, feature),
            id = feature.id
        )
    }

//    /**
//     * Prepares row before putting into $del table.
//     */
//    internal fun xyzDel(OLD: PgRow) {
//        val txn = session.version()
//        val txnTs = session.versionTime()
//        OLD.txn = txn.txn
//        OLD.txn_next = txn.txn
//        OLD.flags = Flags(OLD.flags!!).action(Action.DELETED)
//        OLD.author = session.options.author ?: session.options.appId
//        if (session.options.author != null) {
//            OLD.author_ts = txnTs
//        }
//        if (OLD.created_at != null) {
//            OLD.created_at = OLD.updated_at
//        }
//        OLD.updated_at = txnTs
//        OLD.app_id = session.options.appId
//        OLD.uid = session.nextUid()
//        val currentVersion: Int = OLD.version ?: 1
//        OLD.version = currentVersion + 1
//    }

}
