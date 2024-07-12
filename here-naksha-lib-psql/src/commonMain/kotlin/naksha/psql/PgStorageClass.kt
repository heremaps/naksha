package naksha.psql

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The storage class.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgStorageClass : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgStorageClass::class

    override fun initClass() {
    }

    companion object {
        /**
         * The storage class for collections that should be consistent.
         */
        @JsStatic
        @JvmField
        val Consistent = defIgnoreCase(PgStorageClass::class, "consistent") { self ->
            self.persistence = "p"
        }.alias(PgStorageClass::class, "p")

        /**
         * The storage class for collections that need to be fast, but where data loss is acceptable in case that the database crashes.
         * **WARNING**: When a collection with this storage-class is created, and the database crashes, all data will be lost. The data
         * is not lost, when the database is correctly shutdown.
         */
        @JsStatic
        @JvmField
        val Brittle = defIgnoreCase(PgStorageClass::class, "brittle") { self ->
            self.persistence = "u"
        }.alias(PgStorageClass::class, "u")

        /**
         * The storage class for collections that should be ultra-fast, and only live for the current session. When the connection is
         * closed, the collections will be deleted. As the [PgConnection] is pooled, they may survive longer than expected, and it is
         * often recommended to intentionally drop them, before releasing the connection.
         */
        @JsStatic
        @JvmField
        val Temporary = defIgnoreCase(PgStorageClass::class, "temporary") { self ->
            self.persistence = "t"
        }.alias(PgStorageClass::class, "t")
    }

    /**
     * The PostgresQL persistence character.
     * - `p`: permanent table/sequence
     * - `u`: unlogged table/sequence
     * - `t`: temporary table/sequence
     */
    var persistence: String = "p"
        private set
}