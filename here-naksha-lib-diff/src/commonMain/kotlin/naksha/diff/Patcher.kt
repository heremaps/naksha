package naksha.diff

import naksha.base.Any_TYPE
import naksha.base.Platform
import naksha.base.illegalArg
import kotlin.jvm.JvmStatic

class Patcher private constructor() {

    companion object Patcher_C {

        /**
         * Applies supplied [Difference] on given object, effectively patching it.
         * @param toBePatched object to be patched
         * @param diff [Difference] to be applied as patch on the object
         * @return [toBePatched] - modified after patching
         * @since 3.0.0
         *
         * See sample below:
         * ```
         * val someObject = fromJson(""""
         * {
         *     "foo": "bar",
         *     "lorem": "ipsum"
         *  }
         * """")
         *
         * val diff = MapDiff()
         * diff["foo"] = UpdateOp(oldValue = "bar", newValue = "new_bar")
         * diff["lorem"] = RemoveOp(oldValue = "ipsum")
         * diff["new_field"] = InsertOp(newValue = 123)
         *
         * Patcher.patch(someObject, diff)
         *
         * assert(someObject.toJson() == """
         * {
         *     "foo": "new_bar",
         *     "new_field": 123
         * }
         * """)
         * ```
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T> patch(toBePatched: T, diff: Difference?): T {
            val patchIt = Platform.box(Platform.unbox(toBePatched), Any_TYPE)
            if (diff == null) {
                return toBePatched
            }
            when (patchIt) {
                is MutableMap<*, *> -> {
                    if (diff !is MapDiff) throw illegalArg("Patch failed, the object is a Map, but the difference is not DiffMap")
                    patchMap(patchIt as MutableMap<Any, Any?>, diff, ArrayList()) as T
                }

                is MutableList<*> -> {
                    if (diff !is ListDiff) throw illegalArg("Patch failed, the object is a List, but the difference is not ListDiff")
                    patchList(patchIt as MutableList<Any?>, diff, ArrayList()) as T
                }

                else -> throw illegalArg("Object to patch is not Map or List, unable to patch")
            }
            return toBePatched
        }

        @Suppress("UNCHECKED_CAST")
        private fun patchMap(toBePatched: MutableMap<Any, Any?>, diff: MapDiff, path: ArrayList<Any>) {
            for ((diffKey, diffValue) in diff.differences) {
                when(diffValue){
                    is InsertDiff -> {
                        toBePatched[diffKey] = diffValue.newValue
                    }
                    is RemoveDiff -> {
                        toBePatched.remove(diffKey)
                    }
                    is UpdateDiff -> {
                        toBePatched[diffKey] = diffValue.newValue
                    }
                    is ListDiff -> {
                        path.add(diffKey)
                        val list = toBePatched[diffKey]
                        if (list !is MutableList<*>) throw illegalArg("Expect list at ${path.joinToString("->")}")
                        patchList(list as MutableList<Any?>, diffValue, path)
                        path.removeLast()
                    }
                    is MapDiff -> {
                        path.add(diffKey)
                        val map = toBePatched[diffKey]
                        if (map !is MutableMap<*, *>) throw illegalArg("Expect map at ${path.joinToString("->")}")
                        patchMap(toBePatched[diffKey] as MutableMap<Any, Any?>, diffValue, path)
                        path.removeLast()
                    }

                    else -> {
                        path.add(diffKey)
                        throw illegalArg("The given map contains invalid element at ${path.joinToString("->")}")
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun patchList(toBePatched: MutableList<Any?>, diff: ListDiff, path: ArrayList<Any>) {
            val differences = diff.differences
            for (index in differences.size - 1 downTo 0) {
                when(val diffEntry = differences[index]) {
                    null -> continue
                    is UpdateDiff -> {
                        toBePatched[index] = diffEntry.newValue
                    }
                    is RemoveDiff -> {
                        toBePatched.removeAt(index)
                    }
                    is InsertDiff -> {
                        toBePatched.add(diffEntry.newValue)
                    }
                    is ListDiff -> {
                        path.add(index)
                        val element = toBePatched[index]
                        if (element !is MutableList<*>) throw illegalArg("Expect list at ${path.joinToString("->")}")
                        patchList(element as MutableList<Any?>, diffEntry, path)
                        path.removeLast()
                    }
                    is MapDiff -> {
                        path.add(index)
                        val element = toBePatched[index]
                        if (element !is MutableMap<*, *>) throw illegalArg("Expect map at ${path.joinToString("->")}")
                        patchMap(element as MutableMap<Any, Any?>, diffEntry, path)
                        path.removeLast()
                    }
                    else -> {
                        path.add(index)
                        throw illegalArg("The given list contains invalid element at ${path.joinToString("->")}")
                    }
                }
            }
        }
    }
}