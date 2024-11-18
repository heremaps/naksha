package naksha.diff

class Patcher private constructor() {

    companion object Patcher_C {

        fun <T : Any> patch(toBePatched: T, diff: Difference?): T {
            if (diff == null) {
                return toBePatched
            }
            when (toBePatched) {
                is Map<*, *> -> {
                    require(diff is MapDiff) { "Patch failed, the object is a Map, but the difference is no DiffMap" }
                    return patchMap(toBePatched, diff) as T
                }
            }
        }

        private fun <T> patchMap(toBePatched: Map<*, *>, diff: MapDiff): Map<*, *> {

        }
    }
}