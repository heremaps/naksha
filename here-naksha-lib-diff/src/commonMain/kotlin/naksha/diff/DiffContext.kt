@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import kotlin.js.JsExport

/**
 * Context used by [DifferenceCalculator] when calculating difference in [DifferenceCalculator.calculateDifference].
 *
 * @since 3.0
 */
@JsExport
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
     * Compares two doubles.
     *
     * Depending on the yielded result, [DifferenceCalculator] will decide whether there is a difference for particular property.
     *
     * @param first The first double to compare.
     * @param second The second double to compare
     * @return true if the values should be treated as equal, false otherwise.
     */
    fun equalsDouble(first: Double, second: Double): Boolean
}