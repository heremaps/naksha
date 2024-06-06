package com.here.naksha.lib.naksha.request

/**
 * Logical operation between elements 1..n
 */
import com.here.naksha.lib.naksha.request.LOpType.Companion.AND
import com.here.naksha.lib.naksha.request.LOpType.Companion.NOT
import com.here.naksha.lib.naksha.request.LOpType.Companion.OR
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

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
        fun and(vararg children: Op): LOp {
            return LOp(AND, *children)
        }

        fun or(vararg children: Op): LOp {
            return LOp(OR, *children)
        }

        fun not(op: Op): LOp {
            return LOp(NOT, op)
        }
    }
}
