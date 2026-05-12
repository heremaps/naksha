package naksha.common.test

import kotlin.jvm.JvmStatic

object CommonTestConstants {
    private const val DEFAULT_TEST_STORAGE_ID: String = "local_psql_test_storage"
    private const val TEST_STORAGE_ID_ENV: String = "NAKSHA_TEST_STORAGE_ID"

    @JvmStatic
    fun getTestStorageId(): String =
        currentEnvironment()[TEST_STORAGE_ID_ENV]?.takeUnless { it.isBlank() } ?: DEFAULT_TEST_STORAGE_ID
}

internal expect fun currentEnvironment(): Map<String, String>
