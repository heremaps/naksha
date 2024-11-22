package naksha.diff

class DifferenceFilter private constructor() {

    companion object DifferenceFilter_C {

        /**
         * Removes all occurences of [RemoveOp] from composite [Difference] types as [ListDiff] or [MapDiff]
         * Note that supplied [difference] will be affected (it is also returned)
         *
         * @param difference [Difference] to be filtered
         * @since 3.0.0
         */
        fun removeAllRemoveOp(difference: Difference?) {
            when (difference) {
                is MapDiff -> removeAllRemoveOpFromMap(difference)
                is ListDiff -> removeAllRemoveOpFromList(difference)
            }
        }

        private fun removeAllRemoveOpFromMap(mapDiff: MapDiff) {
            removeAllRemoveOpFromIterator(mapDiff.iterator()) { it.value }
        }

        private fun removeAllRemoveOpFromList(listDiff: ListDiff) {
            removeAllRemoveOpFromIterator(listDiff.iterator()) { it }
        }

        private fun <T> removeAllRemoveOpFromIterator(
            iterator: MutableIterator<T>,
            diffRetrieval: (T) -> Difference?
        ) {
            while (iterator.hasNext()) {
                when (val diff = diffRetrieval(iterator.next())) {
                    is RemoveOp -> iterator.remove()
                    else -> removeAllRemoveOp(diff)
                }
            }
        }
    }
}