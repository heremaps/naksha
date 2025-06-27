package naksha.psql

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The kind of PostgresQL database table.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgKind : PlatformEnum() {
    companion object PgKind_C {
        /**
         * The [PlatformType] of [PgKind].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgKind::class).withPackageName(PACKAGE_NAME)

        /**
         * Ordinary table.
         */
        @JsStatic
        @JvmField
        val OrdinaryTable = def(TYPE, "r")

        /**
         * Partitioned table, so the table has is partitioned via PARTITION BY, the children will be [OrdinaryTable]'s, except they're partitioned again, only then another [PartitionedTable] is found. Generally, the lowest level of partitioning should always be an [OrdinaryTable].
         */
        @JsStatic
        @JvmField
        val PartitionedTable = def(TYPE, "p")

        @JsStatic
        @JvmField
        val ForeignTable = def(TYPE, "f")

        @JsStatic
        @JvmField
        val View = def(TYPE, "v")

        @JsStatic
        @JvmField
        val MaterializedView = def(TYPE, "m")

        @JsStatic
        @JvmField
        val Index = def(TYPE, "i")

        @JsStatic
        @JvmField
        val PartitionedIndex = def(TYPE, "I")

        @JsStatic
        @JvmField
        val Sequence = def(TYPE, "S")

        @JsStatic
        @JvmField
        val ToastTable = def(TYPE, "t")

        @JsStatic
        @JvmField
        val CompositeType = def(TYPE, "c")

        /**
         * Returns the kind as read from `relkind` from `pg_class` table.
         * @param kind the `relkind` as read form `pg_class`.
         * @return the kind.
         */
        @JsStatic
        @JvmStatic
        fun of(kind: String?): PgKind = PlatformEnum.get(kind, TYPE)
    }

    override fun namespace() = TYPE
    override fun initClass() {}
}