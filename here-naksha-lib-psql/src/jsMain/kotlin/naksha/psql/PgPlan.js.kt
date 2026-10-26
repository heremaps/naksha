package naksha.psql

import naksha.base.illegalState

actual class PgPlan private actual constructor(): AutoCloseable {

    internal constructor(plan: PreparedPlan): this() {
        this.plan = plan
    }

    private var plan: PreparedPlan? = null
    private var batched: Array<Array<Any?>?>? = null

    actual fun setFetchSize(size: Int) {
        // In JavaScript, we can ignore this call.
    }

    actual fun execute(args: Array<Any?>?): PgCursor {
        val plan = this.plan ?: throw illegalState("Cursor is closed")
        return PgCursor(if (args != null) plan.cursor(args) else plan.cursor())
    }

    actual fun addBatch(args: Array<Any?>?) {
        var batched = this.batched
        if (batched == null) {
            batched = arrayOf(args)
        } else {
            batched.unsafeCast<dynamic>().push(args)
        }
        this.batched = batched
    }

    actual fun executeBatch(): IntArray {
        val batched = this.batched ?: throw illegalState("No batch added")
        this.batched = null
        var result = IntArray(0)
        plv8.subtransaction {
            result = IntArray(batched.size) { i ->
                val args = batched[i]
                (if (args == null) execute() else execute(args)).unsafeCast<Int>()
            }
        }
        return result
    }

    actual override fun close() {
        val plan = this.plan
        if (plan != null) {
            this.plan = null
            plan.free()
        }
    }
}