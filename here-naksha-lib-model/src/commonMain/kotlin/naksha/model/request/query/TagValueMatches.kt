@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Tests if the value of a tag match the given regular expression.
 * @since 3.0.0
 */
@JsExport
class TagValueMatches() : TagQuery() {
    /**
     * Tests if the value of a tag match the given regular expression.
     *
     * This operation is equal to the GIN index operation [`@?`](https://www.postgresql.org/docs/current/functions-json.html) using a regular expression query.
     *
     * Examples:
     *
     * ```Kotlin
     * TagMatches("foo", "^[a-z][0-9]+$")
     * ```
     * ```Java
     * new TagMatches("foo", "^[a-z][0-9]+$")
     * ```
     *
     * @param name the name of the tag.
     * @param regex the regular expression against which the tag value should be matched; requires that the value is a string.
     * @since 3.0.0
     */
    @JsName("of")
    constructor(name: String, regex: String) : this() {
        this.name = name
        this.regex = regex
    }

    companion object TagExists_C {
        private val REGEX = NotNullProperty<TagValueMatches, String>(String::class) { _, _ -> ".*" }
    }

    /**
     * The regular expression against which the tag value should be matched; requires that the value is a string.
     * @since 3.0.0
     */
    var regex by REGEX

} // -> naksha_tags(flags,tags) @? $[?(@.key=~/regex/)]
