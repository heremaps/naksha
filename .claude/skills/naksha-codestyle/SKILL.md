---
name: naksha-codestyle
description: Instructions how to write code.
---

## Code
Use two spaces instead of tabs.
Avoid using streaming APIs, prefer classic loops like:
```kotlin
for (v in array) ...

var i = -1
while (++i < array.size) ...

for (i in 0 until array.size) ...
```
Chose dependent on use-case, but stay away from streaming API, unless there is a specific reason.
Try to avoid looping the same array or list multiple times, merge loops when possible.
Always copy variables to stack, when used more than one time, example:
```kotlin
// NO:
fun get(key: String, alternative: T): T {
  return if (this[key] != null && alternative::class.isInstance(this[key])) this[key] else alternative 
}
// YES:
fun get(key: String, alternative: T): T {
    val value = this[key]
    return if (value != null && alternative::class.isInstance(value)) value else alternative
}
```

## Comments
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
