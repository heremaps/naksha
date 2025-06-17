@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A SQL `WHERE` query.
 * @since 3.0
 */
internal data class PgQueryWhereClause(
    /**
     * The `WHERE` query, without the keyword `WHERE` or an empty string, if an empty query (query without conditions).
     * @since 3.0
     */
    val where: String,

    /**
     * The arguments to used with the WHERE in order.
     * @since 3.0
     */
    val argValues: MutableList<Any?>,

    /**
     * The types of the arguments.
     * @since 3.0
     */
    val argTypes: MutableList<PgType>,
) {
    companion object PgQueryWhereClause_C {
        /**
         * The [PlatformType] of [PgQueryWhereClause].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgQueryWhereClause::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Returns the [argTypes] as typed-array _(`Array<String>`)_.
     * @since 3.0
     */
    val argTypeNames: Array<String>
        get() = argTypes.map(PgType::toString).toTypedArray()
}