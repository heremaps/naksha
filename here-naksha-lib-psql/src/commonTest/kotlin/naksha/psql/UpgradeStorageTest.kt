package naksha.psql

import naksha.base.Int64
import naksha.model.Naksha
import naksha.model.NakshaVersion
import naksha.model.objects.NakshaStorage
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class UpgradeStorageTest : PgTestBase() {

    private fun assertStorageVersion(expect: NakshaVersion) {
        // Ensure that the current version is what we expect.
        storage.adminConnection().use { conn ->
            conn.execute("SELECT naksha_version() AS v").fetch().use { cursor ->
                val installedVersion: Int64 = cursor["v"]
                assertEquals(expect.toInt64(), installedVersion)
            }
        }
    }

    @Test
    fun tryToUpgradeStorage() {
        assertStorageVersion(adminVersion)

        // Downgrade storage.
        val downgradeVersion = NakshaVersion.of("1.0.0")
        val downgradeConfig = NakshaStorage.fromJSON("""{
  "id": "local_psql_test_storage",
  "className": "naksha.psql.PsqlTestStorage",
  "version": "$downgradeVersion"
}""").proxy(PgConfig::class)
        Naksha.setupStorage(downgradeConfig)
        assertStorageVersion(downgradeVersion)

        // Upgrade storage again.
        Naksha.setupStorage(env.storageConfig)
        assertStorageVersion(adminVersion)
    }
}
