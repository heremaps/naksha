package naksha.model.request.condition

/**
 * Logical operation between elements 1..n
 */
import naksha.model.request.condition.LOpType.LOpTypeCompanion.AND
import naksha.model.request.condition.LOpType.LOpTypeCompanion.NOT
import naksha.model.request.condition.LOpType.LOpTypeCompanion.OR
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class LOp: Op {
    val op: LOpType
    val children: MutableList<Op> = mutableListOf()

    internal constructor(op: LOpType) {
        this.op = op
    }

    internal constructor(op: LOpType, vararg children: Op) {
        this.op = op
        this.children.addAll(children)
    }

    /**
     * Adds the given children.
     * @param child The children to add.
     * @return this.
     */
    fun add(child: Op): LOp {
        children.add(child)
        return this
    }

    override fun getType(): OpType {
        return op
    }
    
    companion object {
        @JvmStatic
        fun and(vararg children: Op): LOp {
            return LOp(AND, *children)
        }
        @JvmStatic
        fun or(vararg children: Op): LOp {
            return LOp(OR, *children)
        }
        @JvmStatic
        fun not(op: Op): LOp {
            return LOp(NOT, op)
        }
    }
}
