package naksha.psql

import naksha.model.SessionOptions

/**
 * When being in Java, we implement support for parallel executions.
 */
class PsqlSession(storage: PgStorage, options: SessionOptions, readOnly: Boolean) : PgSession(storage, options, readOnly) {
    // TODO: Implement parallel execution on top of the shared code (shared with PLV8/JavaScript)
}