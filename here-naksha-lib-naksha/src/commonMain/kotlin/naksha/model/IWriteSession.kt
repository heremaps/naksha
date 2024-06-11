package naksha.model

import naksha.model.response.Response
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// FIXME TODO move it to proper library

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class IWriteSession(
    context: NakshaContext,
    stmtTimeout: Int,
    lockTimeout: Int
): IReadSession(context, stmtTimeout, lockTimeout) {

    abstract fun writeFeature(feature: NakshaFeatureProxy): Response

    abstract fun commit()

    abstract fun rollback()

    abstract fun close()
}