package naksha.psql

/**
 * Immutable physical settings for creating a collection root and its partition set.
 *
 * This value contains no collection metadata, connection, table hierarchy, or executable SQL.
 * All settings are create-only: passing different settings for an existing root does not alter it.
 * Naksha validates these settings and configures pg_partman, which owns history and performance
 * partition provisioning and maintenance. Callers do not choose an initial partition version.
 *
 * @property storeHistory whether to register finite history ranges with pg_partman. When false,
 * only HEAD storage is created; history range settings are not used to provision partitions.
 * @property partitions number of feature-number range groups to configure with pg_partman (1..256);
 * 1 disables fn subpartitioning.
 * @property shift exponent defining each finite nv range width as `1L shl shift`.
 * @property retain number of history ranges to retain; retention width is interval times retain.
 * @property backward negative backward-coverage setting for pg_partman configuration.
 * @property forward non-negative number of future ranges to premake.
 * @property storageClass requested physical durability/storage policy; PostgreSQL persistence and
 * partition compatibility are handled by the creation implementation.
 * @property tablespace unquoted physical tablespace name selected for [storageClass], or null to use
 * PostgreSQL's configured default. Applies to physical storage and future partition provisioning.
 * @property toastTupleTarget TOAST target in bytes for storage-bearing tables; do not apply leaf-only
 * storage parameters to a partitioned root. Derived from the initialized database's tuple-size limit.
 * @since 3.0
 */
internal data class PgCollectionCreateOptions(
    val storeHistory: Boolean,
    val partitions: Int,
    val shift: Int,
    val retain: Int,
    val backward: Int,
    val forward: Int,
    val storageClass: PgStorageClass,
    val tablespace: String?,
    val toastTupleTarget: Int
)
