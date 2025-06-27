package naksha.psql

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The storage class.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgStorageClass : PlatformEnum() {
    companion object PgStorageClass_C {
        /**
         * The [PlatformType] of [PgStorageClass].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgStorageClass::class).withPackageName(PACKAGE_NAME)

        /**
         * The storage class for collections that should be consistent and long time stored, being fault-tolerant.
         */
        @JsStatic
        @JvmField
        val Consistent = defIgnoreCase(TYPE, "consistent") { self ->
            self.persistence = "p"
        }.alias<PgStorageClass>("p")

        /**
         * The storage class for collections that need to be fast, but should survive a server crash. Ephemeral collections are stored locally with the database server, that means, when the hardware of the server, or the EC2 instance in AWS, fails, the data will be lost.
         *
         * **WARNING**: When a collection with this storage-class is created, and the database crashes, all data will be lost. The data is not lost, when the database is shutdown correctly. Brittle data can get lost due to other reasons, it is not backed up or stored at multiple places.
         */
        @JsStatic
        @JvmField
        val Ephemeral = defIgnoreCase(TYPE, "ephemeral") { self ->
            self.persistence = "e"
        }.alias<PgStorageClass>("e")

        /**
         * The storage class for collections that need to be very fast, but where a data loss is acceptable in case that the database erver crashes. Most of the data is kept in memory, and only written back to disk when PostgresQL shutdown normally, or when shared buffers are needed. If there is ephemeral storage, brittle collections are stored on ephemeral. A brittle collection is available across sessions, and except for the weak storage guarantees, it is a normal collection.
         *
         * **WARNING**: When a collection with this storage-class is created, and the database crashes, all data will be lost. The data is not lost, when the database is shutdown correctly. Brittle data can get lost due to other reasons, it is not backed up or stored at multiple places.
         */
        @JsStatic
        @JvmField
        val Brittle = defIgnoreCase(TYPE, "brittle") { self ->
            self.persistence = "u"
        }.alias<PgStorageClass>("u")

        /**
         * The storage class for collections that should be ultra-fast, and only live for the current session. When the connection is closed, the collections will be deleted. As the [PgConnection] is pooled, they may survive longer than expected, and it is often recommended to intentionally drop them, before releasing the connection. This data is normally stored in the same tablespace as [Brittle].
         */
        @JsStatic
        @JvmField
        val Temporary = defIgnoreCase(TYPE, "temporary") { self ->
            self.persistence = "t"
        }.alias<PgStorageClass>("t")

        /**
         * When the storage class is unknown.
         */
        @JsStatic
        @JvmField
        val Unknown = defIgnoreCase(TYPE, "unknown")

        /**
         * Detect storage class from `relpersistence` from `pg_class` or by official names.
         * @param value the value as read from `pg_class` or as defined in [naksha.model.objects.NakshaCollection].
         * @return detected storage class.
         */
        @JsStatic
        @JvmStatic
        fun of(value: String?): PgStorageClass = get(value, TYPE)
    }

    override fun namespace() = TYPE
    override fun initClass() {}

    /**
     * The PostgresQL persistence character.
     * - `p`: permanent table/sequence
     * - `e`: ephemeral table/sequence
     * - `u`: unlogged (brittle) table/sequence
     * - `t`: temporary table/sequence
     */
    var persistence: String = "p"
        private set
}