---
name: naksha-proxy
description: Generate a Naksha proxy (ObjectProxy / ListProxy / MapProxy) or a JsEnum class that follows the conventions used throughout `here-naksha-lib-*`. Use when the user asks to create a new proxy, model class, request type, or enumeration that wraps `PlatformObject` / `PlatformList` data, or asks to add typed accessors over an existing one.
---

# Naksha proxy / JsEnum skill

This skill encodes the conventions for writing typed wrappers over `PlatformObject` / `PlatformList` in the Naksha codebase, plus the conventions for typed enumerations.

## Core concepts

- The JSON parser returns raw `PlatformObject` (map-like, exposed in lib-base as `AnyObject` which extends `MapProxy<String, Any>`) and `PlatformList` (list-like, exposed as `ListProxy<T>`).
- A **proxy** is a typed view over one of those. The runtime data is the raw platform value; the proxy is a thin facade that exposes typed getters and setters via delegated properties. Apply a proxy at runtime via `anyObject.proxy(MyProxy::class)`.
- Proxies are **cached** by the underlying map: `obj.proxy(Foo::class) === obj.proxy(Foo::class)`. They are bound to the same map once and never unlinked.
- Proxies are **not thread-safe**; only one thread accesses a given instance at a time.
- An **enumeration** extends `naksha.base.JsEnum`. Values are registered statically through `def(...)` or `defIgnoreCase(...)`. They serialize as plain strings/ints in JSON.
- Both flavors are `@JsExport`-ed because the same code runs on JVM and JS.
- For every property, the canonical surface a caller can rely on is four methods: `has<Name>()`, `get<Name>()`, `set<Name>(value)`, `remove<Name>()`. Kotlin's `var name by DELEGATE` compiles to `getName`/`setName` automatically; `hasName`/`removeName` must be written explicitly. Generate all four for every property unless the user explicitly asks for a slimmer surface.

## When NOT to use

- Plain Kotlin data classes that never round-trip through JSON parsing. Use `data class` instead.
- Static configuration that doesn't need to be exported to JS — use `companion object` / `enum class`.

## Workflow

Before generating anything, do these in order:

1. **Confirm the kind**. Ask which is wanted if unclear:
   - **Object proxy** — typed view over a JSON object (most common)
   - **List proxy** — typed view over a JSON array of homogeneous elements
   - **Map proxy** — typed view over a JSON map with non-string keys (rare)
   - **JsEnum** — closed set of named string/int values
2. **Confirm the property list** (for proxies): name, kotlin type, nullable, default value, optional JSON key override.
3. **Locate the target module/package**. Naksha's convention is one file per public type, package mirrors directory, package name is lowercase `naksha.<module>.<sub>`. Use `grep -R "<NeighborClass>" here-naksha-lib-*/src/commonMain/kotlin` to find the right neighbor.
4. **Generate the file** using the matching template below.
5. **Validate** with the checklist at the end.

## Object proxy template

A typed view over an `AnyObject` (= `MapProxy<String, Any>`). Use this 90% of the time.

```kotlin
@file:Suppress("OPT_IN_USAGE")

package <package>

import naksha.base.*
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * One short sentence describing what this models.
 * @since 3.0
 */
@JsExport
open class <ClassName> : AnyObject() {

    /**
     * Doc for [propName]. Mention units, range, defaults, and {Create-Only} if relevant.
     * @since 3.0
     */
    var <propName>: <Type> by <DELEGATE>

    /** True iff the underlying map has an entry for [propName]. */
    fun has<PropName>(): Boolean = hasRaw("<jsonKey>")

    /** Remove [propName] from the underlying map; returns this for chaining. */
    fun remove<PropName>(): <ClassName> {
        removeRaw("<jsonKey>")
        return this
    }

    /** Fluent setter for [propName]; returns this for chaining. */
    fun with<PropName>(value: <Type>): <ClassName> {
        <propName> = value
        return this
    }

    // ...repeat for each property

    companion object <ClassName>_C {
        // One private delegate per property; placing them in companion lets the Kotlin
        // compiler inline the getter/setter calls.
        private val <DELEGATE> = NotNullProperty<<ClassName>, <Type>>(<Type>::class) { _, _ -> <default> }
        // For nullable string property with no default:
        // private val STRING_NULL = NullableProperty<<ClassName>, String>(String::class)
        // For nullable property that should be REMOVED from the underlying map when set to null:
        // private val PROP_NULL = NullableProperty<<ClassName>, Foo>(Foo::class, autoRemove = true)
        // For nullable property that should be auto-created on first read:
        // private val PROP_NULL = NullableProperty<<ClassName>, NakshaList>(NakshaList::class, autoCreate = true)
        // For an enum-typed property:
        // private val MODE = NotNullEnum<<ClassName>, StoreMode>(StoreMode::class) { _, _ -> StoreMode.ON }
        // private val MODE_NULL = NullableEnum<<ClassName>, StoreMode>(StoreMode::class)
    }
}
```

`<jsonKey>` is the same string the underlying map uses. It defaults to the Kotlin property name; only differs when the delegate is constructed with `name = "<jsonKey>"` (see *Renaming the JSON key* below). `hasRaw` / `removeRaw` bypass the delegate, so they need the wire key directly.

### Delegate choice cheat sheet

| Property declared as | Delegate to use |
|---|---|
| `var x: String by D` | `NotNullProperty<Self, String>(String::class) { _, _ -> "" }` |
| `var x: String? by D` | `NullableProperty<Self, String>(String::class)` |
| `var x: Int by D` | `NotNullProperty<Self, Int>(Int::class) { _, _ -> 0 }` |
| `var x: Int64 by D` | `NotNullProperty<Self, Int64>(Int64::class) { _, _ -> Int64(0) }` |
| `var x: Boolean by D` | `NotNullProperty<Self, Boolean>(Boolean::class) { _, _ -> false }` |
| `var x: MyProxy by D` (auto-created on first read) | `NotNullProperty<Self, MyProxy>(MyProxy::class)` — no `init` lambda; `MyProxy`'s default constructor is invoked the first time the property is read while absent. This is the only way to get auto-create on a non-null proxy-typed property. |
| `var x: MyProxy? by D` | `NullableProperty<Self, MyProxy>(MyProxy::class)` |
| `var x: MyEnum by D` | `NotNullEnum<Self, MyEnum>(MyEnum::class) { _, _ -> MyEnum.DEFAULT }` |
| `var x: MyEnum? by D` | `NullableEnum<Self, MyEnum>(MyEnum::class)` |

### "Required, no default"

`NotNullProperty` always returns a value, so it always needs an initializer. When the user says a property is "required, no default", they usually mean one of three things — pick deliberately:

1. **Sentinel default + downstream validation.** Use `{ _, _ -> "" }` (or `0`, `Int64(0)`, `false`, ...) and rely on a `validate()` method or caller-side check to reject the sentinel before serializing. This is the codebase's prevailing pattern. Use it unless told otherwise.
2. **Treat as nullable internally.** Declare `var x: String? by NullableProperty(...)` and let callers handle absence. Lose type strength but get true "missing".
3. **Throw from the init lambda.** `{ _, name -> throw illegalArg("required property '$name' missing") }`. Use only when reading a missing required property is an actual programmer error rather than user input.

### Renaming the JSON key

The delegate uses the Kotlin property name as the map key by default. **Only pass `name = "<wireKey>"` when the JSON key differs from the Kotlin property name.** Passing `name=` redundantly is noise:

```kotlin
// Correct — Kotlin name `mapId`, wire key "map_id" (snake_case)
private val MAP_ID = NullableProperty<NakshaCollection, String>(String::class, name = "map_id")

// Incorrect — Kotlin name `indexed`, wire key "indexed". The `name =` is redundant; omit it.
private val INDEXED = NotNullProperty<Foo, Boolean>(Boolean::class, name = "indexed") { _, _ -> true }
```

If you do override the wire key, use the same string for both the `name =` argument *and* every `hasRaw("...")` / `removeRaw("...")` call — those bypass the delegate and need the raw map key.

## List proxy template

Typed view over a `PlatformList` of homogeneous elements.

```kotlin
@file:Suppress("OPT_IN_USAGE")

package <package>

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A list of [ElementType].
 * @since 3.0
 */
@JsExport
open class <ClassName>() : ListProxy<<ElementType>>(<ElementType>::class) {

    /** Initializer accepting a vararg of elements. */
    @JsName("fromElements")
    constructor(vararg elements: <ElementType>) : this() {
        addAll(elements.toList())
    }
}
```

The `ListProxy` base already provides `size`, `add`, `addAll`, `set`, `removeAt`, `iterator`, etc.

## Map proxy template

Only when the JSON key is not a `String` (rare). Use:

```kotlin
@JsExport
open class <ClassName> : MapProxy<<KeyType>, <ValueType>>(<KeyType>::class, <ValueType>::class)
```

For `Map<String, Any>` use `AnyObject` (defined as exactly that) and treat it as an object proxy.

## JsEnum template

Closed set of named values that serialize as strings (most common) or ints. Use `defIgnoreCase` if the wire form should be matched without case sensitivity.

```kotlin
@file:Suppress("OPT_IN_USAGE")

package <package>

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * One short sentence describing the enumeration's purpose.
 * - [ON] description of ON
 * - [OFF] description of OFF
 * @since 3.0
 */
@JsExport
class <ClassName> : JsEnum() {

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = <ClassName>::class

    override fun initClass() {}

    companion object <ClassName>_C {
        /** Doc for [ON]. */
        @JvmField
        val ON = defIgnoreCase(<ClassName>::class, "on")

        /** Doc for [OFF]. */
        @JvmField
        val OFF = defIgnoreCase(<ClassName>::class, "off")
    }
}
```

### `def` vs `defIgnoreCase`

- `def(Klass, "Value")` — case-sensitive. Use when the wire form is fixed (e.g. uppercase constants, ints).
- `defIgnoreCase(Klass, "value")` — case-insensitive on input but emits exactly what's passed on output. Default choice for human-typed config values.

### Integer-valued enums

Use `def(Klass, intValue)` instead of a string. The companion still uses `@JvmField val FOO = ...`.

### Subclass per value (legacy)

A few legacy enums use one Kotlin subclass per constant (see `here-naksha-lib-base/src/commonMain/kotlin/naksha/base/JsEnum.kt` doc block). Do **not** use this pattern for new code — single-class with `companion object` constants is the current convention.

## File placement

- Package follows the directory: `here-naksha-lib-<module>/src/commonMain/kotlin/naksha/<module>/.../<ClassName>.kt`
- One public class per file.
- Filename matches the class name.
- New types that downstream code already references go next to their nearest neighbor in the existing directory tree.

## Conventions checklist (run before declaring done)

1. **Header**: `@file:Suppress("OPT_IN_USAGE")` is present. Add `"LeakingThis"` if `init {}` writes to `this` properties.
2. **Class is `@JsExport`** and `open class` (proxies must allow subclassing for `proxy()` to work).
3. **Companion** is named `<ClassName>_C` (matches the rest of the codebase).
4. **Companion delegates are `private val`** — never `internal` or public.
5. **One delegate per property**. Sharing a single `STRING_NULL` delegate across multiple properties is fine *only* if their JSON key matches their Kotlin name and they share an initializer.
6. **All public properties have KDoc** ending in `@since 3.0`. Use exactly `3.0` (two components) — that's what `NakshaCollection.kt` / `NakshaFeature.kt` and the rest of the model module use. A few older files use `3.0.0`; ignore them.
7. **No constructor logic that reads properties** — proxies are usually instantiated by `<obj>.proxy(MyProxy::class)`, and the constructor runs against an empty `AnyObject`. Use delegate `init = { _, _ -> ... }` for defaults instead.
8. **`with<Prop>` returns `<ClassName>`**, not the base class. If the proxy extends another proxy that already has a `with<Prop>`, override with `as <ClassName>` cast (see `NakshaCollection.withId`).
9. **Enums use `@JvmField`** on companion constants. Add `@JsStatic` only if JS callers need direct access (most don't — they go through `JsEnum.get`).
10. **Validate the package compiles**: `nix develop --command bash -c 'cd naksha && gradle :here-naksha-lib-<module>:compileKotlinJvm 2>&1 | tail -30'` (the build script ignores `kotlin.compiler.execution.strategy` warnings).

## Examples in the codebase

Use these as exact references for the conventions, not as code to copy:

- Object proxy: `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/objects/NakshaCollection.kt`
- Object proxy with enum + auto-create: `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/objects/NakshaFeature.kt`
- List proxy: `here-naksha-lib-base/src/commonMain/kotlin/naksha/base/StringList.kt`
- JsEnum (small): `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/objects/StoreMode.kt`
- JsEnum (with intValue and aliases): `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/Action.kt`
- Delegate definitions: `here-naksha-lib-base/src/commonMain/kotlin/naksha/base/NotNullProperty.kt`, `NullableProperty.kt`, `NotNullEnum.kt`, `NullableEnum.kt`

## Common pitfalls

- **Forgetting `@JsExport`** — JS callers can't see the class, but JVM still works, so this slips through unless you actually run the JS target.
- **Putting initializer code in the constructor body** — for proxies, prefer the `init = { _, _ -> default }` lambda on the delegate. The constructor often runs in contexts (deserialization, `proxy()` cast) where reading other properties would yield defaults.
- **Sharing one delegate across properties with different defaults** — if the values diverge later, the shared delegate produces wrong defaults silently. Keep one delegate per property unless they truly are identical.
- **Using `kotlin.enum`** — the project uses `JsEnum`. A Kotlin `enum class` does not survive JSON round-tripping the way `JsEnum` does.
- **Adding `has<Name>()` / `remove<Name>()` for every property** — only add them when the property is genuinely optional in the wire format. Required properties don't need them; the delegate's default handles presence.
- **Renaming a property without updating the JSON key** — if the property name was the JSON key, renaming silently breaks serialization. Either keep the old name as `name=` on the delegate, or include both in a deprecation cycle.
