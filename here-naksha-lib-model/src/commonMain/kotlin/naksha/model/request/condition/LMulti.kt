@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Logical operation between child elements `1..n`.
 */
@Suppress("UNCHECKED_CAST")
@JsExport
abstract class LMulti<T, SELF : LMulti<T, SELF>>(vararg children: IQuery<T>) : LOp<T, SELF>() {
    /**
     * The children that are combined.
     */
    @Suppress("SENSELESS_COMPARISON")
    @JvmField
    val children: MutableList<IQuery<T>> = if (children != null) mutableListOf(*children) else mutableListOf()

    /**
     * Add the given children.
     * @param children the children to add.
     * @return this
     */
    fun add(vararg children: IQuery<T>): SELF {
        for (child in children) {
            this.children.add(child)
        }
        return this as SELF
    }

    /**
     * Remove all given children.
     * @param children the children to remove.
     * @return this
     */
    fun remove(vararg children: IQuery<T>): SELF {
        for (child in children) {
            this.children.remove(child)
        }
        return this as SELF
    }
}
