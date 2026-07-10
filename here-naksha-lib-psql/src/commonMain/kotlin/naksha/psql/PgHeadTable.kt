package naksha.psql

import naksha.base.Int64
import naksha.model.objects.StandardMembers.StandardMembers_C.Id
import naksha.psql.PgColumn.PgColumn_C.FN
import naksha.psql.PgColumn.PgColumn_C.NEXT_VERSION
import naksha.psql.PgColumn.PgColumn_C.VERSION
import naksha.psql.PgUtil.PgUtilCompanion.partitionNumber
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * The _HEAD_ table of a [collection][naksha.model.objects.NakshaCollection], storing the latest [tuple][naksha.model.Tuple] of the [features][naksha.model.objects.NakshaFeature] being part of the [collection][naksha.model.objects.NakshaCollection].
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgHeadTable(
    /** The collection to which this HEAD table belongs. */
    collection: PgCollection,
) : PgTable(collection, collection.id, null) {

    /**
     * All distribution partitions; if not partitioned, then an empty array.
     * @since 3.0
     */
    @JvmField
    val partitions: Array<PgDistributionPartition> = if (collection.partitions <= 1) emptyArray() else Array(collection.partitions) {
        PgDistributionPartition(this, it)
    }

    // Constraint names derive from [tableName]; each distribution partition passes its own name so its
    // constraints stay unique. The partitioned parent has none — its partition key is an expression.
    @Suppress("FunctionName")
    internal fun CONSTRAINT(tableName: String = name): String {
        val ID = collection.column(Id)
        return """
  CONSTRAINT ${quoteIdent(tableName, "\$c_pkey")} PRIMARY KEY ($FN) INCLUDE ($VERSION, $ID),
  CONSTRAINT ${quoteIdent(tableName, "\$c_fn")} CHECK (($FN < 0 AND $ID IS NOT NULL) OR ($FN >= 0 AND $ID IS NULL)),
  CONSTRAINT ${quoteIdent(tableName, "\$c_nv")} CHECK ($NEXT_VERSION IS NULL),
  CONSTRAINT ${quoteIdent(tableName, "\$c_id")} UNIQUE ($ID) INCLUDE ($VERSION, $FN)"""
    }

    @Suppress("FunctionName")
    internal fun CONSTRAINT(tableName: String, distributionPartition: Int): String {
        return """${CONSTRAINT(tableName)},
  CONSTRAINT ${quoteIdent(tableName, "\$c_fnr")} CHECK ((($FN & 65535)::int4 % ${collection.partitions})=$distributionPartition)
  """
    }

    override fun CREATE_SQL(): String {
        val (CREATE_TABLE, TABLESPACE) = CREATE_TABLE_and_TABLESPACE()
        val ID = collection.column(Id)

        // HEAD is NOT distribution partitioned.
        if (partitions.isEmpty()) return """$CREATE_TABLE $quotedName (${columnDefinitions()}, ${CONSTRAINT()})
WITH (fillfactor=50,toast_tuple_target=$toast_tuple_target)$TABLESPACE;
CREATE INDEX IF NOT EXISTS ${quoteIdent(name, "\$i_version")} ON $quotedName USING btree ($VERSION) INCLUDE ($FN, $ID);"""

        // HEAD is distribution partitioned.
        return """$CREATE_TABLE $quotedName (${columnDefinitions()})
PARTITION BY RANGE ((($FN & 65535)::int4 % ${collection.partitions}))$TABLESPACE;"""
    }

    /**
     * Calculate the distribution-partition into which to write the feature with the given feature-id.
     * @param featureId the ID of the feature to locate the performance partition for.
     * @return either the performance partition to put the feature into; _null_ if the table is not partitioned, features need to be written into the table itself.
     */
    @JsName("getByFeatureId")
    operator fun get(featureId: String): PgDistributionPartition?
        = if (partitions.isEmpty()) null else partitions[partitionNumber(featureId) % partitions.size]

    @JsName("getByFeatureNumber")
    operator fun get(featureNumber: Int64): PgDistributionPartition?
            = if (partitions.isEmpty()) null else partitions[featureNumber.toInt() % partitions.size]

    override fun create(conn: PgConnection) {
        super.create(conn)
        for (partition in partitions) partition.create(conn)
    }

    override fun createIndex(conn: PgConnection, index: PgIndex) {
        if (!partitions.isEmpty()) {
            for (partition in partitions) partition.createIndex(conn, index)
        } else {
            super.createIndex(conn, index)
        }
        if (index !in indices) indices += index
    }

    override fun addIndex(index: PgIndex) {
        if (!partitions.isEmpty()) {
            for (partition in partitions) partition.addIndex(index)
        } else {
            super.addIndex(index)
        }
        if (index !in indices) indices += index
    }

    override fun removeIndex(index: PgIndex) {
        if (!partitions.isEmpty()) {
            for (partition in partitions) partition.removeIndex(index)
        } else {
            super.removeIndex(index)
        }
        if (index in indices) indices -= index
    }

    override fun dropIndex(conn: PgConnection, index: PgIndex) {
        if (!partitions.isEmpty()) {
            for (partition in partitions) partition.dropIndex(conn, index)
        } else {
            super.dropIndex(conn, index)
        }
        if (index in indices) indices -= index
    }
}