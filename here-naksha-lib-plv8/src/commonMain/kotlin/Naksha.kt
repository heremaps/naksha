@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * To be called once per storage to initialize a storage. This is normally only done from the Java code that invokes
 * the _JvmPlv8Env.install(conn,version,schema,storageId)_ method. The purpose of this method is to create all the
 * tables that are essentially needed by the [NakshaSession], so the table for the transactions, the table for the
 * global dictionaries and the table for the collection management.
 */
@JsExport
object Naksha {
    fun initStorage(sql: IPlv8Sql, schema:String) {
        val schemaOid : Int = asMap(asArray(sql.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schema)))[0])["oid"]!!
        val schemaQuoted = sql.quoteIdent(schema)
        val query = """
SET SESSION search_path TO $schemaQuoted, public, topology;
CREATE TABLE IF NOT EXISTS naksha_global (
    id          text        PRIMARY KEY NOT NULL,
    data        bytea       NOT NULL
);
CREATE SEQUENCE IF NOT EXISTS naksha_txn_seq AS int8;
CREATE TABLE IF NOT EXISTS naksha_txn (
    txn         int8         PRIMARY KEY NOT NULL,
    ts          timestamptz  NOT NULL DEFAULT transaction_timestamp(),
    xact_id     xid8         NOT NULL DEFAULT pg_current_xact_id(),
    app_id      text         COMPRESSION lz4 NOT NULL,
    author      text         COMPRESSION lz4 NOT NULL,
    seq_id      int8,
    seq_ts      timestamptz,
    version     int8,
    details     bytea       COMPRESSION lz4,
    attachment  bytea       COMPRESSION lz4
) PARTITION BY RANGE (txn);
CREATE INDEX IF NOT EXISTS naksha_txn_ts_idx ON naksha_txn USING btree ("ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_app_id_ts_idx ON naksha_txn USING btree ("app_id" ASC, "ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_author_ts_idx ON naksha_txn USING btree ("author" ASC, "ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_seq_id_idx ON naksha_txn USING btree ("seq_id" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_seq_ts_idx ON naksha_txn USING btree ("seq_ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_version_idx ON naksha_txn USING btree ("version" ASC);
-- Create the table for the collection management as normal collection.
-- This is a chicken-egg problem:
-- We need to create this collection before we can create collections, because
-- when a collection is created, this table will be checked. So we need to ensure
-- that this collection is created without this check, but as a valid collection.
-- Note: We do not allow deletion of naksha tables!
do $$
    var commonjs2_init = plv8.find_function("commonjs2_init");
    commonjs2_init();
    let naksha = require("naksha");
    naksha.JsPlv8Env.Companion.initialize();
    let sql = new naksha.JsPlv8Sql();
    if (!naksha.NakshaSession.Companion.tableExists(sql, 'naksha_collections', $schemaOid)) {
        naksha.NakshaSession.Companion.collectionCreate(sql, 'naksha_collections', false, false);
    }
$$ LANGUAGE 'plv8';
"""
        sql.execute(query)
    }

    /**
     * Array to fasten partition id.
     */
    val PARTITION_ID = Array<String>(256) {
        if (it < 10) "00$it" else if (it < 100) "0$it" else "$it"
    }

    /**
     * Returns the lock-id for the given name.
     * @param name The name to query the lock-id for.
     * @return The 64-bit FNV1a hash.
     */
    fun lockId(name: String): BigInt64 = Fnv1a64.string(Fnv1a64.start(), name)

    /**
     * Returns the partition number.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as number between 0 and 255.
     */
    fun partitionNumber(id: String): Int = Fnv1a32.string(Fnv1a32.start(), id) and 0xff

    /**
     * Returns the partition id as three digit string.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as three digit string.
     */
    fun partitionNameForId(id: String): String = PARTITION_ID[partitionNumber(id)]

}