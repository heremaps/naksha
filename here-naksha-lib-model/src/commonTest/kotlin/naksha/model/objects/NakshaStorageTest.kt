package naksha.model.objects

import naksha.base.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NakshaStorageTest {
    companion object NakshaStorageTest_C {

        const val TEST_DOWNWARD_COMPATIBILITY = """{
  "id": "storage-to-delete",
  "type": "Storage",
  "title": "UniMap Moderation Dev Storage",
  "description": "UniMap Moderation storage for Dev environment, holding collections managed by Naksha service",
  "className": "naksha.psql.PsqlStorage",
  "properties": {
    "dbConfig": {
      "host": "localhost",
      "db": "test_db",
      "user": "test_user",
      "password": "test_password",
      "port": 1234,
      "schema": "test_schema",
      "minPoolSize": 5,
      "maxPoolSize": 50
    }
  }
}"""
    }

    @Test
    fun shouldCreateAValidStorage() {
        val storage = assertNotNull(Platform.fromJson(TEST_DOWNWARD_COMPATIBILITY, NakshaStorage.TYPE))
        assertEquals(storage.id, "storage-to-delete")
        // TODO: Verify that the storage is correct configured.
        //       To say, gather the master-uri, and access of master property should return valid object
        //       for localhost and other configured data !!!
        //       Test that all values are at the correct place using getters, even while in the underlying
        //       JSON they are on deprecated, no longer supported, places (undocumented features!)
    }
}