package naksha.psql

@Suppress("ArrayInDataClass")
internal data class PgWriterPlan(val pgPlan: PgPlan, val sql: String, val typeNames: Array<String>)