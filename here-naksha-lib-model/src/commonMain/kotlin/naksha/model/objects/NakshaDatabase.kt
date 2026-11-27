package naksha.model.objects

import naksha.base.Id
import naksha.model.IStorage
import kotlin.js.JsExport

/**
 * A database is a logical concept in Naksha. It represents a set of [catalogs][NakshaCatalog], [collections][NakshaCollection], and [documents][naksha.base.AnyObject] _(specifically [features][NakshaFeature])_. A database is unique within a given environment. There must not be two databases with the same `id`, but different content, in the same environment.
 *
 * A [storage][IStorage] is the combination of the logical database and an actual physical storage location. An instance of `NakshaDatabase` can only be acquired from [IStorage.database]. The physical location at which the data of this database is stored is implementation specific, and normally specified through the constructor of the storage.
 *
 * Multiple instances of the same database _(with the same `id`)_ can be stored in multiple places, but only one storage should be the master mutating the database; all others are treated as read-only replicas. Therefore, all physical storage locations should contain the same data.
 *
 * Imagine a [storage][IStorage] as a thin wrapper around an [SQLite](https://sqlite.org/index.html) database file, or around a JDBC connection pool to a specific [PostgreSQL database](https://www.postgresql.org/docs/current/tutorial-concepts.html). Any physical location is technically possible, like [S3](https://aws.amazon.com/s3/), [DynamoDB](https://aws.amazon.com/dynamodb/), [Redis](https://redis.io/), and more.
 *
 * ### Logical Replication
 * This concept allows to store the same data multiple times, each time using individual optimized indices in different physical locations. In a nutshell this means that multiple [storages][IStorage] can contain the same database, and therefore the same data _(only one can be the [master][isMaster])_.
 *
 * Each instance of a database can store the very same collection, with the same `id`, even the very same features with the same `id`. However, the collection can have different configurations for each storage, therefore, each instance can have different [members][Member] and [indices][Index], while storing the very same features with the same [identifiers][Id] and [tuple-numbers][naksha.base.TupleNumber]. The stored [Tuple][naksha.model.Tuple] will logically be the same. They will hash to the same value and when serialized into `JSON`, they will result in the exact same `JSON` document.
 * @since 3.0
 * @see IStorage.database
 * @see naksha.model.NakshaDatabaseImpl
 */
@JsExport
interface NakshaDatabase {
    /**
     * The storage in which the database is located.
     * @since 3.0
     */
    val storage: IStorage

    /**
     * The unique identifier of the database.
     *
     * For historic reasons, this often is the same as the storage identifier, but with Naksha `v3` this is technically no longer necessary.
     * @since 3.0
     */
    val id: Id

    /**
     * If this database is a replication.
     *
     * Technically a replica must still be mutable somehow _(otherwise new data could not be replicated)_, but logically the replica is read-only.
     *
     * Apart from this, replicas will allow the management of catalogs and collections _(for partial replication or replication with different members and indices)_. They as well allow subsets of documents. They generally allow streaming into or from them.
     *
     * So read-only just means that no new documents or states must be produced for replicated data. Replicas may allow to manage additional collections and features that do not exist in the source database.
     *
     * Beware that there are as well pure replicas, where nothing can be changed, so real read-only replicas, for example offered when using `lib-psql` with [AWS Aurora](https://aws.amazon.com/rds/aurora/).
     * @since 3.0
     */
    val isReplica: Boolean

    /**
     * If this database is the master.
     *
     * **Note**: This does not guarantee that it is mutable!
     * @since 3.0
     */
    val isMaster: Boolean
}