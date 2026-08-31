package naksha.psql

import naksha.base.Platform
import naksha.model.Naksha
import naksha.model.NakshaVersion
import naksha.model.objects.NakshaStorage
import kotlin.test.Test
import kotlin.test.assertEquals

class UpgradeStorageTest : PgTestBase() {

    private fun assertStorageVersion(expect: NakshaVersion) {
        // Ensure that the current version is what we expect.
        storage.adminConnection().use { conn ->
            conn.execute("SELECT naksha_version() AS v").fetch().use { cursor ->
                val installedVersion: Long = cursor["v"]
                assertEquals(expect.toLong(), installedVersion)
            }
        }
    }

    @Test
    fun tryToUpgradeStorage() {
        assertStorageVersion(adminVersion)

        // Downgrade storage, we need `override` instruction for this.
        val downgradeVersion = NakshaVersion.of("1.0.0")
        val downgradeConfig = NakshaStorage.fromJSON("""{
  "id": "${Platform.getTestStorageId()}",
  "className": "naksha.psql.PsqlTestStorage",
  "version": "$downgradeVersion",
  "override": true
}""").proxy(PgConfig::class)
        Naksha.setupStorage(downgradeConfig)
        assertStorageVersion(downgradeVersion)

        // Upgrade storage again, this time we should not need override.
        Naksha.setupStorage(storageConfig)
        assertStorageVersion(adminVersion)
    }
}
