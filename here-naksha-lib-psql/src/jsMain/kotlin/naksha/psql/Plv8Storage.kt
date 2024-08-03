@file:Suppress("OPT_IN_USAGE")

package naksha.psql

/**
 * The PLV8 implementation of a storage, will be added to `plv8.storage`.
 */
@JsExport
class Plv8Storage : PgStorage(Plv8Cluster, js("plv8.execute('select naksha_default_schema() as s')[0].s").unsafeCast<String>()) {
}
