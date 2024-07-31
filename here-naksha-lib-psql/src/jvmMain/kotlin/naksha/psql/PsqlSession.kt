package naksha.psql

/**
 * When being in Java, we implement support for parallel executions.
 */
class PsqlSession(storage: PgStorage, options: PgOptions) : PgSession(storage, options) {
}