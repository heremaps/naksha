package naksha.base

import kotlin.js.JsExport

/**
 * An abstraction of a multi-platform iterator above platform object entries.
 *
 * Usage example for a map iterator _(actually, even while you can iterate an array, it is not very useful there)_:
 * ```kotlin
 * val it = map_iterator(map)
 * var next = it.next()
 * while (!next.done) {
 *   val entry: PlatformList = next.value
 *   val key = array_get(entry, 0)
 *   val value = array_get(entry, 1)
 *   // Do something
 *   next = it.next()
 * }
 * ```
 *
 */
@Suppress("OPT_IN_USAGE")
@JsExport
abstract class PlatformIterator<VALUE> {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Iteration_protocols#the_iterator_protocol
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Iterator

    /**
     * Tests if there is another entry and returns it. In JavaScript the method will return a new instance for every invocation,
     * while at the JVM it will only load the next value into a cached entry-instance and always return the same instance.
     *
     * To be multi-platform, always instantly consume the returned entry, never keep a reference to it. Always assume it will be
     * modified ones [next] is called again. Actually, reusing the returned object does not work in JavaScript, while keeping
     * reference to returned object for later usage does not work on the JVM.
     *
     * Example:
     * ```kotlin
     * val it = map_iterator(map)
     * var next = it.next()
     * while (!next.done) {
     *   val entry: PlatformList = next.value
     *   val key = array_get(entry, 0)
     *   val value = array_get(entry, 1)
     *   // Do something
     *   next = it.next()
     * }
     * ```
     *
     * @return The iterator with the next value, can be _this_ or a new instance.
     */
    abstract fun next(): PlatformIteratorResult<VALUE>
}