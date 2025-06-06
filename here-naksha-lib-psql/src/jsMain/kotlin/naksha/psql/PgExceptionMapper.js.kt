@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.psql

import naksha.base.NakshaException

actual class PgExceptionMapper {
    actual companion object PgExceptionMapper_C {
        actual fun map(throwable: Throwable, sql: String?): NakshaException {
            TODO("Not yet implemented")
        }
    }
}