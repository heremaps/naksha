package naksha.diff

class DifferenceFilter private constructor(){

    companion object DifferenceFilter_C {

        fun removeAllRemoveOp(mapDiff: MapDiff): MapDiff {
            val iterator = mapDiff.iterator()
            while (iterator.hasNext()){
                val diff = iterator.next().value
                when(diff){
                    is RemoveOp -> iterator.remove()
                    is MapDiff -> removeAllRemoveOp(diff)
                    is ListDiff -> removeAllRemoveOp(diff)
                }
            }
            return mapDiff
        }

        fun removeAllRemoveOp(listDiff: ListDiff): ListDiff {
            val iterator = listDiff.iterator()
            while (iterator.hasNext()){
                when(val diff = iterator.next()){
                    is RemoveOp -> removeFrom(iterator)
                    is MapDiff -> removeAllRemoveOp(diff)
                    is ListDiff -> removeAllRemoveOp(diff)
                }
            }
            return listDiff
        }

        private fun removeFrom(iterator: MutableIterator<*>){
            iterator.remove()
        }
    }
}