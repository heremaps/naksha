package naksha.diff

import kotlin.math.abs

/**
 * Context used by [DifferenceCalculator] when calculating difference in [DifferenceCalculator.calculateDifference]
 */
interface DiffContext {

    /**
     * Tests whether the given key should be ignored by the [DifferenceCalculator].
     *
     * @param key The key in question.
     * @param sourceMap The source map.
     * @param targetOrPatchMap The target map, or the partial patch map.
     * @return true if the key should be ignored; false otherwise.
     */
    fun ignore(key: Any, sourceMap: Map<*, *>, targetOrPatchMap: Map<*, *>): Boolean

    /**
     * Compares two numbers. Depending on the yielded result, [DifferenceCalculator] will decide whether there is a difference for particular numeric property.
     *
     * @param first first number to compare
     * @param second second number to compare
     * @return true if there should be treated as equal, false otherwise
     */
    fun areTwoNumbersEqual(first: Number, second: Number): Boolean

    /**
     * Default implementation of [DiffContext] used by [DifferenceCalculator].
     */
    object Default: DiffContext {

        /**
         * Assumes that all properties should be analyzed when calculating difference
         */
        override fun ignore(key: Any, sourceMap: Map<*, *>, targetOrPatchMap: Map<*, *>): Boolean =
            false

        /**
         * Treats two numbers as equal if the difference between them is less or equal to 1e-6
         */
        override fun areTwoNumbersEqual(first: Number, second: Number): Boolean {
            if (first is Float || first is Double || second is Float || second is Double) {
                return abs(first.toDouble() - second.toDouble()) < 1e-6
            }
            return first.toLong() == second.toLong()
        }
    }
}
