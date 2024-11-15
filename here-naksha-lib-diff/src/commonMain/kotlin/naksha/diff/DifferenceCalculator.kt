package naksha.diff

import kotlin.math.abs

class DifferenceCalculator private constructor() {
    companion object DifferenceCalculator_C {

        /**
         * Returns the difference of the two states or null, if both entities are equal. This method will
         * return {@link InsertOp} if the source state is null, {@link RemoveOp} if the target state is
         * null, {@link MapDiff} if both states are {@link Map maps} that differ, {@link ListDiff} if both
         * states are {@link List lists} that differ and {@link UpdateOp} if the two states are different,
         * but none of them is null and not both of them are {@link Map} or {@link List}.
         *
         * @param sourceState first object with the source state to be compared against second target
         *                    state.
         * @param targetState the target state against which to compare the source state.
         * @param ignoreKey   a method to test for keys to ignore while creating the difference.
         * @return the difference between the two states or null, if both states are equal.
         */
        fun calculateDifference(
            source: Any?,
            target: Any?,
            ignoreKey: IgnoreKey? = null
        ): Difference? {
            if (source == null) {
                return InsertOp(newValue = target)
            }
            if (target == null) {
                return RemoveOp(oldValue = source)
            }
            if (source is Map<*, *> && target is Map<*, *>) {
                return calculateMapDifference(source, target, ignoreKey)
            }
            if (source is List<*> && target is List<*>) {
                return calculateListDifference(source, target, ignoreKey)
            }
            if (source is Number && target is Number && areTwoIdenticalNumbers(source, target)) {
                return null
            }
            if (source == target) {
                return null
            }
            return UpdateOp(oldValue = source, newValue = target)
        }

        private fun calculateMapDifference(
            source: Map<*, *>,
            target: Map<*, *>,
            ignoreKey: IgnoreKey?
        ): MapDiff? {
            val resultDiff = MapDiff()
            for ((sourceKey, sourceValue) in source) {
                if (sourceKey == null || ignoreKey.ignore(sourceKey, source, target)) {
                    continue
                }
                if (sourceKey !in target) {
                    resultDiff[sourceKey] = RemoveOp(sourceValue)
                } else {
                    val entryDiff = calculateDifference(sourceValue, target[sourceKey], ignoreKey)
                    if (entryDiff != null) {
                        resultDiff[sourceKey] = entryDiff
                    }
                }
            }
            for ((targetKey, targetValue) in target) {
                if (targetKey == null ||
                    targetKey in source ||
                    ignoreKey.ignore(targetKey, source, target)
                ) {
                    continue
                }
                resultDiff[targetKey] = InsertOp(targetValue)
            }
            return if (resultDiff.isEmpty()) {
                null
            } else {
                resultDiff
            }
        }

        private fun IgnoreKey?.ignore(
            key: Any,
            sourceMap: Map<*, *>,
            targetOrPatchMap: Map<*, *>
        ): Boolean {
            if(this == null){
                return false
            }
            return this.ignore(key, sourceMap, targetOrPatchMap)
        }

        private fun calculateListDifference(
            source: List<*>,
            target: List<*>,
            ignoreKey: IgnoreKey?
        ): ListDiff? {
            val resultDiff = ListDiff()
            val sourceSize = source.size
            val targetSize = target.size
            resultDiff.originalLength = sourceSize
            resultDiff.newLength = targetSize
            val lastCommonIndex = minOf(sourceSize, targetSize) - 1
            var isModified = false

            // calculate diffs for common indices
            (0..lastCommonIndex).forEach { index ->
                val entryDiff = calculateDifference(source[index], target[index], ignoreKey)
                if (entryDiff != null) {
                    isModified = true
                }
                resultDiff.add(entryDiff)
            }
            // if source is longer than target, fill missing entries with removals
            if (sourceSize > targetSize) {
                isModified = true
                (lastCommonIndex + 1..<sourceSize).forEach { index ->
                    resultDiff.add(RemoveOp(source[index]))
                }
            }
            // if target is longer than source, fill missing entries with insertions
            else if (targetSize > sourceSize) {
                isModified = true
                (lastCommonIndex + 1..<targetSize).forEach { index ->
                    resultDiff.add(InsertOp(target[index]))
                }
            }
            if (!isModified) {
                return null
            }
            return resultDiff
        }

        private fun areTwoIdenticalNumbers(first: Number, second: Number): Boolean {
            if (first is Float || first is Double || second is Float || second is Double) {
                return abs(first.toDouble() - second.toDouble()) < 1e-6
            }
            return first.toLong() == second.toLong()
        }
    }
}
