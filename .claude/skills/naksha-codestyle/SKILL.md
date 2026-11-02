---
name: naksha-codestyle
description: Instructions how to write code.
---

# General
Use two spaces instead of tabs.
If a file uses a mixed or other amount of spaces per tab, or even tabs, modify the file to that it uses 2 spaces per tab. Do not modify files that you do not touch, we do not want unnecessary changes.
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
This avoids an `else`, which makes it easier to read and follow the code for a human. Clearly, this is not always possible, but when code can be written in that way, prefer it.

# Comments and Markdown
Do not add line breaks within paragraphs. For example:
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
