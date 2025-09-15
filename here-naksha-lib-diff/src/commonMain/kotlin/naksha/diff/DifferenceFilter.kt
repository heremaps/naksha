package naksha.diff

import kotlin.jvm.JvmStatic

class DifferenceFilter private constructor() {

    companion object DifferenceFilter_C {

        /**
         * Removes all occurrences of [RemoveOp] from [MapDiff] types within the supplied [Difference].
         * Note that supplied [difference] will be affected (it is also returned)
         *
         * @param difference [Difference] to be filtered
         * @since 3.0.0
         */
        @JvmStatic
        fun removeAllRemoveOpFromMaps(difference: Difference?) {
            when (difference) {
                is MapDiff -> filterOutMapRemovals(difference)
            }
        }

        private fun filterOutMapRemovals(mapDiff: MapDiff) {
            removeAllRemoveOpFromIterator(mapDiff.iterator()) { it.value }
        }

        private fun <T> removeAllRemoveOpFromIterator(
            iterator: MutableIterator<T>,
            diffRetrieval: (T) -> Difference?
        ) {
            while (iterator.hasNext()) {
                when (val diff = diffRetrieval(iterator.next())) {
                    is RemoveOp -> iterator.remove()
                    else -> removeAllRemoveOpFromMaps(diff)
                }
            }
        }
    }
}