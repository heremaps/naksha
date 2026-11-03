---
name: naksha-codestyle
description: Instructions how to write code.
---

# Format
- Use two spaces instead of tabs.
- If a file uses a mixed or other amount of spaces per tab, or even tabs, modify the file to that it uses 2 spaces per tab.
  - Do not modify files that you do not touch.
  - Do the modification in two phases, first do the changes intended, ones approved, make the reformat on yourself in a second commit that states in the commit message that this is only a formatting. This simplifies reviews.

# General
Avoid using streaming APIs, prefer classic loops like:
```kotlin
for (v in array) ...

var i = -1
while (++i < array.size) ...

for (i in 0 until array.size) ...
```
Chose, dependent on use-case, but stay away from streaming API, unless there is a specific reason.
Try to avoid looping the same array or list multiple times, merge loops when possible.
Always copy variables to stack when used more than one time, example:
```kotlin
// Do not:
fun get(key: String, alternative: T): T {
  return if (this[key] != null && alternative::class.isInstance(this[key])) this[key] else alternative 
}
// Do instead:
fun get(key: String, alternative: T): T {
  val value = this[key]
  return if (value != null && alternative::class.isInstance(value)) value else alternative
}
```
Avoid nested functions when they can be normal functions or companion functions. If there is a reason for nesting, it is fine, but avoid not necessary nested functions. 

# If-Else
Avoid `if-else` blocks, when a return can be used. For example instead of this:
```kotlin
fun ensureSameMeaning(a: String?, b: String?) {
  if (a == null || b == null) {
    if (a !== b) throw IllegalArgumentException("Not same")
  } else {
    // Some more code that is more complex
  }
}
```
Rather do this:
```kotlin
fun ensureSameMeaning(a: String?, b: String?) {
  if (a == null || b == null) {
    if (a !== b) throw IllegalArgumentException("Not same")
    return
  }
  // Some more code that is more complex
}
```

# Comments and Markdown
Do not add line breaks within paragraphs of comments. For example:
```kotlin
/**
 * This is comment with a first paragraph. We will not do any line-break to adjust for certain columns. This practise is from the last century, all modern IDEs are able to automatically add line breaks.
 *
 * However, we still want separate paragraphs for sure.
 */
```
Always keep documentation up-to-date.
Always add the current version as `@since {major}.{minor}` to methods documentation like:
```kotlin
/**
 * Function documentation ...
 *
 * @since 3.0
 */ 
```

# Missing information
If you miss database credentials or other credentials: Ask the user for the missing credentials. Do not guess to make assumptions.

# Error handling
Do not use Kotlin's build in `require`, because in Naksha we explicitly want all exceptions to be `NakshaException` wrapping a JSON serializable `NakshaError`. Therefore, rather use if. Example:
```kotlin
// Do not:
require( foo == null ) { "Some message" }

// Do instead:
if (foo != null) throw illegalState("Some message")
```
Beware that `NakshaException` has a bunch of helper methods to auto-wrap all kind of errors. If some function is missing, just add it and use it. If you find existing `require` statements in code you anyway modify, treat them like spaces, fix them togehter with the space adjustment in a dedicated commit, so that it is clear that this is only syntactic sugar.

# PostgresQL database
Avoid at all cost to use SQL functions, except for build-in functions. This is especially true for all SQL functions that are using any other language than pure pl/SQL _(the native integrated language)_. Functions using PLv8 are purely intended for special purpose, for example to be used as background job from `pg_cron`. The reason is that starting the `PLv8` engine _(or most other custom language engines)_ is so expensive, compared to what they should archive, we **MUST** avoid this.

For example, we store feature-numbers in the database as feature-ordinals _(which are the feature-numbers byte-swapped, with the sign-bit flipped)_. When querying these numbers, turn the feature-number into a feature-ordinal in Java, then use the ordinal. We know that all `fn` columns store the ordinals, so we can perfectly fine do the convert in Java/JVM. DO NOT USE the SQL function that can do the same, it will cost microseconds, but they can be very valuable.
