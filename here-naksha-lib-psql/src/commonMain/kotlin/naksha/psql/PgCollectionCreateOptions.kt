package naksha.psql

import naksha.base.Version
import naksha.base.illegalArg

/**
 * Immutable physical settings for creating a collection root and its partition set.
 *
 * This value contains no collection metadata, connection, table hierarchy, or executable SQL.
 * All settings are create-only: passing different settings for an existing root does not alter it.
 * Naksha validates these settings before DDL. Its custom SQL maintainer owns history and performance
 * partition provisioning; callers do not choose an initial partition version.
 *
 * @property storeHistory whether to register finite history ranges with the custom maintainer. When false,
 * only HEAD storage is created; history range settings are not used to provision partitions.
 * @property partitions number of physical feature-number range groups (1..256);
 * 1 disables fn subpartitioning.
 * @property shift exponent defining each finite nv range width as `1L shl shift`.
 * @property retain number of history ranges to retain; retention width is interval times retain.
 * @property backward negative backward-coverage setting retained by the model creation contract.
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
) {
    init {
        if (partitions !in 1..256) throw illegalArg("partitions must be in 1..256, got $partitions")
        if (shift !in 12..41) throw illegalArg("shift must be in 12..41, got $shift")
        if (retain <= 1) throw illegalArg("retain must be greater than 1, got $retain")
        if (backward >= 0 || backward.toLong() <= -retain.toLong()) {
            throw illegalArg("backward must be negative and its magnitude less than retain")
        }
        if (forward < 0 || forward >= retain) throw illegalArg("forward must be in 0 until retain")
        if (interval > Long.MAX_VALUE / retain) throw illegalArg("History retention interval overflows bigint")
        if (toastTupleTarget !in MIN_POSTGRES_TOAST_TUPLE_TARGET..MAX_POSTGRES_TOAST_TUPLE_TARGET) {
            throw illegalArg("TOAST target is outside PostgreSQL's supported range: $toastTupleTarget")
        }
        if (tablespace != null && (tablespace.isEmpty() || '\u0000' in tablespace)) {
            throw illegalArg("Tablespace must be a non-empty unquoted identifier without NUL")
        }
        if (storageClass == PgStorageClass.Unknown) throw illegalArg("Unknown storage class")
    }

    /** Finite nv range width; shift is validated before this value is used. */
    val interval: Long
        get() = 1L shl shift

    /** Checked legacy retention setting; does not itself enable automatic history deletion. */
    val retentionInterval: Long
        get() = interval * retain

    /** Align a finite version to its range; HEAD is not a finite history version. */
    fun historyRangeStart(version: Long): Long {
        if (version < 0 || version >= Version.HEAD.number) throw illegalArg("Expected a finite history version")
        return (version shr shift) shl shift
    }

    /** Exclusive bound, clipped at HEAD so the sentinel always belongs only to the default. */
    fun historyRangeEnd(version: Long): Long =
        minOf(historyRangeStart(version) + interval, Version.HEAD.number)
}
