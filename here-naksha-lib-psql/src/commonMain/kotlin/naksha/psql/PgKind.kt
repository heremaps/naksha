package naksha.psql

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The kind of PostgresQL database table.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PgKind : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgKind::class

    override fun initClass() {
    }

    companion object {
        /**
         * Ordinary table.
         */
        @JsStatic
        @JvmField
        val OrdinaryTable = def(PgKind::class, "r")

        /**
         * Partitioned table, so the table has is partitioned via PARTITION BY, the children will be [OrdinaryTable]'s, except they're partitioned again, only then another [PartitionedTable] is found. Generally, the lowest level of partitioning should always be an [OrdinaryTable].
         */
        @JsStatic
        @JvmField
        val PartitionedTable = def(PgKind::class, "p")

        @JsStatic
        @JvmField
        val ForeignTable = def(PgKind::class, "f")

        @JsStatic
        @JvmField
        val View = def(PgKind::class, "v")

        @JsStatic
        @JvmField
        val MaterializedView = def(PgKind::class, "m")

        @JsStatic
        @JvmField
        val Index = def(PgKind::class, "i")

        @JsStatic
        @JvmField
        val PartitionedIndex = def(PgKind::class, "I")

        @JsStatic
        @JvmField
        val Sequence = def(PgKind::class, "S")

        @JsStatic
        @JvmField
        val ToastTable = def(PgKind::class, "t")

        @JsStatic
        @JvmField
        val CompositeType = def(PgKind::class, "c")

        /**
         * Returns the kind as read from `relkind` from `pg_class` table.
         * @param kind the `relkind` as read form `pg_class`.
         * @return the kind.
         */
        @JsStatic
        @JvmStatic
        fun of(kind: String?): PgKind = JsEnum.get(kind, PgKind::class)
    }
}