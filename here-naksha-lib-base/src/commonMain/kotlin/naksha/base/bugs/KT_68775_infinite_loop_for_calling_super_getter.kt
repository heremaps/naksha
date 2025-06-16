package naksha.base.bugs

/**
 * [KT-68775](https://youtrack.jetbrains.com/issue/KT-68775/) - Kotlin/JS infinite loop for calling `super.message`.
 *
 * ## Description
 * Calling a super getter very likely does not work, because when exporting a value to JavaScript, the compiler generate a JavaScript getter property, like:
 * ```js
 * const obj = {
 *   log: ["a", "b", "c"],
 *   get latest() {
 *     return this.log[this.log.length - 1];
 *   },
 * };
 * ```
 * This won't work as expected, because when the getter is overwritten in an extending class, it can't explicitly call the getter of the prototype. If it would try, like `prototype.latest`, it would cause `this` to be the prototype object, not the current object.
 *
 * ## Workaround
 * Whenever a variable is required as `open`, please move the getter _(and optional setter)_ into dedicated protected methods named like:
 *
 * `{name}_(get|set)`
 *
 * ```kotlin
 * open class Foo {
 *   private var msg: String = ""
 *   open var message: String
 *     get() = message_get()
 *   protected open fun message_get(): String = msg
 * }
 * ```
 * This allows in extending classes to override `message_get` and to call the super methods:
 *
 * ```kotlin
 * class Bar : Foo() {
 *   override fun message_get(): String = "The message is: ${super.message_get()}"
 * }
 * ```
 * @since 3.0
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class KT_68775_infinite_loop_for_calling_super_getter
