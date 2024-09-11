package naksha.psql

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
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

    companion object PgStorageClass_C {
        /**
         * The storage class for collections that should be consistent.
         */
        @JsStatic
        @JvmField
        val Consistent = defIgnoreCase(PgStorageClass::class, "consistent") { self ->
            self.persistence = "p"
        }.alias<PgStorageClass>("p")

        /**
         * The storage class for collections that need to be fast, but where data loss is acceptable in case that the database crashes.
         * **WARNING**: When a collection with this storage-class is created, and the database crashes, all data will be lost. The data
         * is not lost, when the database is correctly shutdown.
         */
        @JsStatic
        @JvmField
        val Brittle = defIgnoreCase(PgStorageClass::class, "brittle") { self ->
            self.persistence = "u"
        }.alias<PgStorageClass>("u")

        /**
         * The storage class for collections that should be ultra-fast, and only live for the current session. When the connection is
         * closed, the collections will be deleted. As the [PgConnection] is pooled, they may survive longer than expected, and it is
         * often recommended to intentionally drop them, before releasing the connection.
         */
        @JsStatic
        @JvmField
        val Temporary = defIgnoreCase(PgStorageClass::class, "temporary") { self ->
            self.persistence = "t"
        }.alias<PgStorageClass>("t")

        /**
         * When the storage class is unknown.
         */
        @JsStatic
        @JvmField
        val Unknown = defIgnoreCase(PgStorageClass::class, "unknown")

        /**
         * Detect storage class from `relpersistence` from `pg_class` or by official names.
         * @param value the value as read from `pg_class` or as defined in [naksha.model.objects.NakshaCollection].
         * @return detected storage class.
         */
        @JsStatic
        @JvmStatic
        fun of(value: String?): PgStorageClass = get(value, PgStorageClass::class)
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