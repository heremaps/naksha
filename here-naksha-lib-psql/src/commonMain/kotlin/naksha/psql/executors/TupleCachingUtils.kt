package naksha.psql.executors

import naksha.model.*

object TupleCachingUtils {
    fun cachedTupleNumber(write: WriteExt, tuple: Tuple, tupleList: TupleList, tupleCache: TupleCache): TupleNumber {
        tupleList[write.i] = tuple
        tupleCache.store(tuple)
        return tuple.tupleNumber
    }
}