package naksha.psql.executors.query

class PgQuery(val sql: String, val argValues: Array<Any?>, val argTypes: Array<String>)