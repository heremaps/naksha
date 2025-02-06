package naksha.diff

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
        @JvmStatic
        fun <T : Any> patch(toBePatched: T, diff: Difference?): T {
            if (diff == null) {
                return toBePatched
            }
            when (toBePatched) {
                is MutableMap<*, *> -> {
                    require(diff is MapDiff) { "Patch failed, the object is a Map, but the difference is not DiffMap" }
                    patchMap(toBePatched as MutableMap<Any, Any?>, diff) as T
                }

                is MutableList<*> -> {
                    require(diff is ListDiff) { "Patch failed, the object is a List, but the difference is not ListDiff" }
                    patchList(toBePatched as MutableList<Any?>, diff) as T
                }
                else -> throw IllegalArgumentException("Object to patch is not Map or List, unable to patch")
            }
            return toBePatched
        }

        private fun patchMap(toBePatched: MutableMap<Any, Any?>, diff: MapDiff) {
            diff.forEach { (diffKey, diffValue) ->
                when(diffValue){
                    is InsertOp -> {
                        toBePatched[diffKey] = diffValue.newValue
                    }
                    is RemoveOp -> {
                        toBePatched.remove(diffKey)
                    }
                    is UpdateOp -> {
                        toBePatched[diffKey] = diffValue.newValue
                    }
                    is ListDiff -> {
                        patchList(toBePatched[diffKey] as MutableList<Any?>, diffValue)
                    }
                    is MapDiff -> {
                       patchMap(toBePatched[diffKey] as MutableMap<Any, Any?>, diffValue)
                    }
                    else -> {
                        throw IllegalArgumentException("The given map contains invalid element at key: '$diffKey'")
                    }
                }
            }
        }

        private fun patchList(toBePatched: MutableList<Any?>, diff: ListDiff) {
            for(ind in diff.size - 1 downTo 0){
                when(val diffEntry = diff[ind]){
                    null -> continue
                    is UpdateOp -> {
                        toBePatched[ind] = diffEntry.newValue
                    }
                    is RemoveOp -> {
                        toBePatched.removeAt(ind)
                    }
                    is InsertOp -> {
                        toBePatched.add(diffEntry.newValue)
                    }
                    is ListDiff -> {
                        patchList(toBePatched[ind] as MutableList<Any?>, diffEntry)
                    }
                    is MapDiff -> {
                        patchMap(toBePatched[ind] as MutableMap<Any, Any?>, diffEntry)
                    }
                    else -> {
                        throw IllegalArgumentException("The given list contains invalid element at index: '$ind'")
                    }
                }
            }
        }
    }
}