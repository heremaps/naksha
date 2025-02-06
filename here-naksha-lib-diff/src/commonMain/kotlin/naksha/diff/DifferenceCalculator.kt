package naksha.diff

import kotlin.jvm.JvmOverloads

class DifferenceCalculator private constructor() {
    companion object DifferenceCalculator_C {

        /**
         * Returns the difference of the two states or null, if both entities are equal. This method will
         * return [InsertOp] if the source state is null, [RemoveOp] if the target state is
         * null, [MapDiff] if both states are [Map maps] that differ, [ListDiff] if both
         * states are [List] lists that differ and [UpdateOp] if the two states are different,
         * but none of them is null and not both of them are [Map] or [List].
         *
         * @param source first object with the source state to be compared against second target
         *                    state.
         * @param target the target state against which to compare the source state.
         * @param diffContext context used for comparisons, determines which fields should be ignored and how the numbers should be compared. By default, the [DiffContext.Default] is used
         * @return the difference between the two states or null, if both states are equal.
         * @since 3.0.0
         */
        @JvmOverloads
        fun calculateDifference(
            source: Any?,
            target: Any?,
            diffContext: DiffContext = DiffContext.Default
        ): Difference? {
            if (source == null) {
                return InsertOp(newValue = target)
            }
            if (target == null) {
                return RemoveOp(oldValue = source)
            }
            if (source is Map<*, *> && target is Map<*, *>) {
                return calculateMapDifference(source, target, diffContext)
            }
            if (source is List<*> && target is List<*>) {
                return calculateListDifference(source, target, diffContext)
            }
            if (source is Array<*> && target is Array<*>) {
                return calculateListDifference(source.asList(), target.asList(), diffContext)
            }
            if (source is Number && target is Number && diffContext.areTwoNumbersEqual(
                    source,
                    target
                )
            ) {
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
            diffContext: DiffContext
        ): MapDiff? {
            val resultDiff = MapDiff()
            for ((sourceKey, sourceValue) in source) {
                if (sourceKey == null || diffContext.ignore(sourceKey, source, target)) {
                    continue
                }
                if (sourceKey !in target) {
                    resultDiff[sourceKey] = RemoveOp(sourceValue)
                } else {
                    val entryDiff = calculateDifference(sourceValue, target[sourceKey], diffContext)
                    if (entryDiff != null) {
                        resultDiff[sourceKey] = entryDiff
                    }
                }
            }
            for ((targetKey, targetValue) in target) {
                if (targetKey == null ||
                    targetKey in source ||
                    diffContext.ignore(targetKey, source, target)
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

        private fun calculateListDifference(
            source: List<*>,
            target: List<*>,
            diffContext: DiffContext
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
                val entryDiff = calculateDifference(source[index], target[index], diffContext)
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
    }
}
