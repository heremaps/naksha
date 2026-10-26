package naksha.psql

import naksha.model.*

/**
 * This class only exist for downward compatibility.
 * @since 3.0
 * @see PgStorage
 */
@Deprecated(message = "This class does only exist for downward compatibility")
open class PsqlStorage : PgStorage(), IStorage