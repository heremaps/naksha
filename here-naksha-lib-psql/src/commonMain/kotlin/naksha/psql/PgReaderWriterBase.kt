@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Base class for [PgReader] and [PgWriter] with shared functions.
 * @since 3.0
 */
@JsExport
open class PgReaderWriterBase protected constructor(
    /**
      * The session to which the writer is bound.
      * @since 3.0
      */
    @JvmField
    val session: PgSession,
)