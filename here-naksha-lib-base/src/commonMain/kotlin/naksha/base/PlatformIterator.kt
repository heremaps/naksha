package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * An abstraction of a multi-platform iterator above platform object entries.
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
abstract class PlatformIterator<VALUE> {
    companion object PlatformIteratorCompanion {
        /**
         * The [PlatformType] of [PlatformIterator].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<PlatformIterator<*>> = forKClass(PlatformIterator::class).withPackageName(PACKAGE_NAME)
    }

    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Iteration_protocols#the_iterator_protocol
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Iterator

    /**
     * Return the next entry.
     *
     * ### Implementation Note
     * In JavaScript the method will return a new instance for every invocation, while at the JVM it will only load the next value into a cached entry-instance and always return the same instance.
     *
     * To be multi-platform, always instantly consume the returned entry, never keep a reference to it. Always assume it will be modified ones [next] is called again. Actually, reusing the returned object does not work in JavaScript, while keeping reference to returned object for later usage does not work on the JVM.
     *
     * @return The next value, can be _this_ or a new instance, test [done][PlatformIteratorResult.done] if there is a next value.
     */
    abstract fun next(): PlatformIteratorResult<VALUE>
}