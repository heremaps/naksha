package naksha.psql

external interface

/**
 * Expose the native PLV8 api.
 *
 * - The logger API is already exposed through [naksha.base.Platform.logger]
 * - The `quote_literal` and `quote_ident` methods are as well exposed through [PgPlatform.quote_literal] and [PgPlatform.quote_ident].
 * - See [PLV8 documentation](https://plv8.github.io/)
 */
external interface Plv8 {
    /**
     * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
     * @param string the string to quote.
     * @return the quoted literal.
     */
    fun quote_literal(string: String): String

    /**
     * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
     * @param string the string to quote.
     * @return the quoted ident.
     */
    fun quote_ident(string: String): String

    val DEBUG5: LogLevel
    val DEBUG4: LogLevel
    val DEBUG3: LogLevel
    val DEBUG2: LogLevel
    val DEBUG1: LogLevel
    val LOG: LogLevel
    val INFO: LogLevel
    val NOTICE: LogLevel
    val WARNING: LogLevel
    val ERROR: LogLevel

    /**
     * Emits a message to the client or the PostgreSQL log file.
     * @param level the log-level.
     * @param msg the messages to concat.
     * @see DEBUG5
     * @see DEBUG4
     * @see DEBUG3
     * @see DEBUG2
     * @see DEBUG1
     * @see LOG
     * @see INFO
     * @see NOTICE
     * @see WARNING
     * @see ERROR
     */
    fun elog(level: LogLevel, vararg msg: String)

    /**
     * The plv8 object provides a version string as plv8.version. This string corresponds to the plv8 module version.
     * @since 3.0
     */
    val version: String

    /**
     * Own memory usage.
     * @since 3.0
     */
    fun memory_usage(): MemoryUsage

    /**
     * Run a script from source code, it's like `eval(sourceCode)` but takes a second argument: script name.
     * @since 3.0
     */
    fun run_script(sourceCode: String, scriptName: String)

    /**
     * Executes SQL statement as dedicated subtransaction without arguments and retrieves the results.
     * @param sql The SQL query to execute.
     * @return either an array of objects _(SELECT)_ or the number of rows affected _(INSERT, UPDATE, DELETE, ...)_.
     * @since 3.0
     * @see subtransaction
     */
    fun execute(sql: String): dynamic

    /**
     * Executes SQL statements as dedicated subtransaction and retrieves the results.
     * @param sql The SQL query to execute.
     * @param args The values of the arguments, so to replace `$1` to `$n`.
     * @return either an array of objects _(SELECT)_ or the number of rows affected _(INSERT, UPDATE, DELETE, ...)_.
     * @since 3.0
     * @see subtransaction
     */
    fun execute(sql: String, args: Array<dynamic?>): dynamic

    /**
     * Opens or creates a prepared statement without arguments.
     *
     * @param sql The SQL query to prepare.
     * @return the prepared plan.
     * @since 3.0
     */
    fun prepare(sql: String): PreparedPlan

    /**
     * Opens or creates a prepared statement.
     *
     * The typename parameter is an array where each element is a string that corresponds to the PostgreSQL type name for each bind parameter. Returned value is an object of the PreparedPlan type. This object must be freed by [PreparedPlan.free] before leaving the function.
     * @param sql The SQL query to prepare.
     * @param typeNames The PostgresQL type names, see [PgType].
     * @return the prepared plan.
     * @since 3.0
     */
    fun prepare(sql: String, typeNames: Array<String>): PreparedPlan

    /**
     * Creates a subtransaction and executes the given function for it.
     *
     * If one of the SQL execution in the subtransaction block fails, all operations within the block are rolled back. If the process in the block throws a JavaScript exception, it is carried forward. So use a `try ... catch` block to capture it and do alternative operations if it occurs.
     * @param func the function to execute a subtransaction.
     * @since 3.0
     */
    fun subtransaction(func: () -> Unit)

    /**
     * Returns the Window Function API.
     * @return the Window Function API.
     */
    fun get_window_object(): WindowObject
}

/**
 * The global `plv8` object.
 * @since 3.0
 */
val plv8: Plv8
    get() = js("plv8").unsafeCast<Plv8>()

/**
 * The JavaScript value of `undefined`.
 * @since 3.0
 */
val undefined: dynamic
    get() = js("undefined").unsafeCast<dynamic>()
