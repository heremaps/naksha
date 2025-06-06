@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Any_TYPE
import naksha.base.Platform.PlatformCompanion.box
import naksha.base.PlatformUtil
import naksha.base.PlatformUtil.PlatformUtilCompanion.asSafeDouble
import naksha.base.PlatformUtil.PlatformUtilCompanion.asSafeInt
import naksha.base.PlatformUtil.PlatformUtilCompanion.asSafeInt64
import naksha.base.PlatformUtil.PlatformUtilCompanion.isLogicalDouble
import naksha.base.PlatformUtil.PlatformUtilCompanion.isLogicalInt
import naksha.base.PlatformUtil.PlatformUtilCompanion.isLogicalInt64
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@JsExport
class DifferenceCalculator private constructor() {
    companion object DifferenceCalculator_C {

        /**
         * Returns the difference of the two objects or `null`, if both objects are equal or the same.
         *
         * This method will return [InsertDiff] if the source is `null`, [RemoveDiff] if the target is `null`, [MapDiff] if both objects are maps that differ, [ListDiff] if both object are lists that differ, and [UpdateDiff] in any other case.
         *
         * @param source The source object to be compared against target object.
         * @param target The target object against which to compare the source object.
         * @param diffContext The context used for comparisons, determines which fields should be ignored, and how doubles should be compared. By default, the [DefaultDiffContext.INSTANCE] is used.
         * @return the difference between the two states or null, if both states are equal.
         * @since 3.0.0
         */
        @JvmOverloads
        @JvmStatic
        fun calculateDifference(source: Any?, target: Any?, diffContext: DiffContext = DefaultDiffContext.INSTANCE): Difference? {
            return calculateAnyDifference(box(source, Any_TYPE), box(target, Any_TYPE), diffContext)
        }

        private fun calculateAnyDifference(
            source: Any?,
            target: Any?,
            diffContext: DiffContext = DefaultDiffContext.INSTANCE
        ): Difference? {
            if (source === target) return null
            if (source == null) {
                return InsertDiff(newValue = target)
            }
            if (target == null) {
                return RemoveDiff(oldValue = source)
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
            // Floating point handling
            if (isLogicalDouble(source) || isLogicalDouble(target)) {
                val s = asSafeDouble(source)
                val t = asSafeDouble(target)
                if (s != null && t != null && diffContext.equalsDouble(s, t)) return null
            } else if (isLogicalInt(source) || isLogicalInt(target)) {
                val s = asSafeInt(source)
                val t = asSafeInt(target)
                if (s != null && t != null && s == t) return null
            } else if (isLogicalInt64(source) || isLogicalInt64(target)) {
                val s = asSafeInt64(source)
                val t = asSafeInt64(target)
                if (s != null && t != null && s == t) return null
            }
            if (source == target) {
                return null
            }
            return UpdateDiff(oldValue = source, newValue = target)
        }

        private fun calculateMapDifference(
            source: Map<*, *>,
            target: Map<*, *>,
            diffContext: DiffContext
        ): MapDiff? {
            val resultDiff = MapDiff()
            val differences = resultDiff.differences
            for ((sourceKey, sourceValue) in source) {
                if (sourceKey == null || diffContext.ignore(sourceKey, source, target)) {
                    continue
                }
                if (sourceKey !in target) {
                    differences[sourceKey] = RemoveDiff(sourceValue)
                } else {
                    val entryDiff = calculateAnyDifference(sourceValue, target[sourceKey], diffContext)
                    if (entryDiff != null) {
                        differences[sourceKey] = entryDiff
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
                differences[targetKey] = InsertDiff(targetValue)
            }
            return if (differences.isEmpty()) {
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
            val differences = resultDiff.differences
            val lastCommonIndex = minOf(sourceSize, targetSize) - 1
            var isModified = false

            // calculate diffs for common indices
            for (index in 0..lastCommonIndex) {
                val entryDiff = calculateAnyDifference(source[index], target[index], diffContext)
                if (entryDiff != null) {
                    isModified = true
                }
                differences.add(entryDiff)
            }
            // if source is longer than target, fill missing entries with removals
            if (sourceSize > targetSize) {
                isModified = true
                for (index in lastCommonIndex + 1..<sourceSize) {
                    differences.add(RemoveDiff(source[index]))
                }
            }
            // if target is longer than source, fill missing entries with insertions
            else if (targetSize > sourceSize) {
                isModified = true
                for (index in lastCommonIndex + 1..<targetSize) {
                    differences.add(InsertDiff(target[index]))
                }
            }
            if (!isModified) {
                return null
            }
            return resultDiff
        }
    }
}
