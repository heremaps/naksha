package naksha.base

import kotlin.js.JsExport

/**
 * An abstraction of a multi-platform iterator above platform object entries.
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
     * @return The iterator with the next value, can be _this_ or a new instance.
     */
    abstract fun next(): PlatformIteratorResult<VALUE>
}