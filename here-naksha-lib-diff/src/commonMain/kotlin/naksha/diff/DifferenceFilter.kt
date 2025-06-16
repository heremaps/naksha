package naksha.diff

import kotlin.jvm.JvmStatic

class DifferenceFilter private constructor() {

    companion object DifferenceFilter_C {

        /**
         * Removes all occurrences of [RemoveDiff] from composite [Difference] types as [ListDiff] or [MapDiff]
         * Note that supplied [diff] will be affected (it is also returned)
         *
         * @param diff [Difference] to be filtered
         * @since 3.0.0
         */
        @JvmStatic
        fun removeAllRemoveOp(diff: Difference?) {
            when (diff) {
                is MapDiff -> removeAllRemoveOpFromMap(diff)
                is ListDiff -> removeAllRemoveOpFromList(diff)
            }
        }

        private fun removeAllRemoveOpFromMap(mapDiff: MapDiff) {
            removeAllRemoveOpFromIterator(mapDiff.differences.iterator()) { it.value }
        }

        private fun removeAllRemoveOpFromList(listDiff: ListDiff) {
            removeAllRemoveOpFromIterator(listDiff.differences.iterator()) { it }
        }

        private fun <T> removeAllRemoveOpFromIterator(
            iterator: MutableIterator<T>,
            diffRetrieval: (T) -> Difference?
        ) {
            while (iterator.hasNext()) {
                when (val diff = diffRetrieval(iterator.next())) {
                    is RemoveDiff -> iterator.remove()
                    else -> removeAllRemoveOp(diff)
                }
            }
        }
    }
}