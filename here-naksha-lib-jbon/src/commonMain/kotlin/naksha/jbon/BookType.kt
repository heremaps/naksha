@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * The type of the book as defined in JBON2 specification.
 * @since 3.0
 */
@JsExport
enum class BookType(
    /**
     * The type-number as being used in JBON2 encoding.
     * @since 3.0
     */
    @JvmField val typeNumber: Int
) {
    /**
     * Local book, embedded into the `Tuple` binary.
     * @since 3.0
     */
    LOCAL_BOOK(0),

    /**
     * Members book, provided by the storage, members extrapolated into dedicated storage places. Can be embedded into the `Tuple`.
     * @since 3.0
     */
    MEMBER_BOOK(1),

    /**
     * Global book, persisted in the storage, shared between many `Tuple` to reduce size, compression utility.
     * @since 3.0
     */
    GLOBAL_BOOK(2),
}