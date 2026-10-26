package naksha.psql

// In JavaScript this does nothing.

@JsExport
actual class PgAdminCatalogManager private actual constructor(adminCatalog: PgAdminCatalog) {

    actual companion object PgAdminCatalogManagerCompanion {
        actual fun of(adminCatalog: PgAdminCatalog): PgAdminCatalogManager {
            return PgAdminCatalogManager(adminCatalog)
        }

        actual fun get(adminCatalog: PgAdminCatalog): PgAdminCatalogManager?
            = null
    }
}