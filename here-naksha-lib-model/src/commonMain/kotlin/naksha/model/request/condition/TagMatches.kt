@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Tests if the value of a tag match the given regular expression. This operation is equal to the GIN index operation [`@?`](https://www.postgresql.org/docs/current/functions-json.html) using a regular expression query.
 *
 * Examples:
 * ```Kotlin
 * TagMatches("foo", "^[a-z][0-9]+$")
 * ```
 * ```Java
 * new TagMatches("foo", "^[a-z][0-9]+$")
 * ```
 * @property name the name of the tag (before the equal sign).
 * @property regex the regular expression against which the tag value should be matched; requires that the value is a string.
 */
@JsExport
class TagMatches(@JvmField var name: String, @JvmField var regex: String) : TagQuery() // -> naksha_tags(flags,tags) @? $[?(@.key=~/regex/)]
