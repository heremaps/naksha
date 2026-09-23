package naksha.psql

import naksha.base.Id
import naksha.model.NakshaVersion

/**
 * A wrapper around a `NakshaDatabase` _(which does not yet exist)_.
 *
 * A database is a logical entity in Naksha, the same as the catalog, the collection and the feature. Only the [Tuple][naksha.model.Tuple] is a real physical entity. The concept is that a database can be hosted by multiple storages, but only one storage should be considered the source of truth. All others are treated like replicas or snapshots. They can use different storage layout and indices, optimized for other purposes.
 *
 * To keep the code more maintainable, we move all DDL _(data definition language)_ and SQL code into this class. So, mutation of the PostgresQL schema, tables, indices, and inserting, updating, delete rows. This class should deal with the physical reality of PostgresQL, not with the logical level of the Naksha storage concept. Therefore, this class works with `schema`, `table`, `partition` and `rows`. It should not have any knowledge about the logical part, like that a `row` represents a [Tuple][naksha.model.Tuple], it must not know about books or JBON encoding.
 *
 * Its purpose is to manage low level database entities like `schema`, `table`, `partition` and `rows`. It is fixed to the Naksha needs, so it assumes a primary key above (`nv`, `fn`, `version`). It is strictly tied up to the Naksha storage model. So it requires the three columns `nv`, `fn`, and `version` and it allows arbitrary `PgColumn`. It will understand how the history works, without making any assumptions about the content of a row, so it basically manages rows, each having a unique address, linked to partitioned history.
 * @since 3.0
 */
expect open class PgDatabase(storage: PgStorage, instance: PgInstance, id: Id): AbstractPgDatabase {
    override fun upsert_naksha_admin(
        conn: PgConnection,
        id: Id,
        psql_version: NakshaVersion,
        schema_oid: Int?,
        installed_version: NakshaVersion?
    ): Int
}
