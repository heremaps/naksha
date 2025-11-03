# Lib-Json
A library that is pure Java code and that comes with a hand-crafted JavaScript/TypeScript implementation. The goal is that all libraries being dependent on this basic library, can be translated into JavaScript/TypeScript using [JSweet](https://www.jsweet.org/).

The core concept is to provide an in-memory data model that is very flexible, but as well type safe. Actually this replaces the previous `lib-base`. The central object is now `LibJson`.

## Java/JavaScript to JSON
This library will perform the following data type mappings from Java to JavaScript/JSON:

| Java Type   | JavaScript Type | JSON                                 |
|-------------|-----------------|--------------------------------------|
| JsonSymbol  | [Symbol]        | N/A                                  |
| Exception   | [Error]         | N/A                                  |
| [POJO]      | [Object]        | `{}`                                 |
| `Object[]`  | [Array]         | `[]`                                 |
| JsonMap     | [Map]           | `{}`                                 |
| JsonArray   | [Array]         | `[]`                                 |
| null        | [null]          | `null`                               |
| Byte        | [Number]        | `1`                                  |
| Short       | [Number]        | `1`                                  |
| Int         | [Number]        | `1`                                  |
| Long        | [BigInt]        | `1`                                  |
| Float       | [Number]        | `1.0`                                |
| Double      | [Number]        | `1.0`                                |
| String      | [String]        | `"foo"`                              |
| `boolean[]` | [Array]         | `[]`                                 |
| `byte[]`    | [Uint8Array]    | `"data:Uint8Array;base64,<data>"`    |
| `short[]`   | [Int16Array]    | `"data:Int16Array;base64,<data>"`    |
| `int[]`     | [Int32Array]    | `"data:Int32Array;base64,<data>"`    |
| `long[]`    | [BigInt64Array] | `"data:BigInt64Array;base64,<data>"` |
| `float[]`   | [Float32Array]  | `"data:Float32Array;base64,<data>"`  |
| `double[]`  | [Float64Array]  | `"data:Float64Array;base64,<data>"`  |

Note: All Java arrays are always serialized into arrays _(`[]`)_, except for the primitive arrays, where there is some special handling.

## JSON to Java/JavaScript
When parsing JSON, the library will return the following Java/JavaScript types:

| JSON                                 | Java Type  | JavaScript Type      |
|--------------------------------------|------------|----------------------|
| `{}`                                 | JsonMap    | [Map]                |
| `[]`                                 | JsonArray  | [Array]              |
| `null`                               | null       | [null]               |
| `1`                                  | Long       | [Number] or [BigInt] |
| `1.0`                                | Double     | [Number]             |
| `"data:Uint8Array;base64,<data>"`    | `byte[]`   | [Uint8Array]         |
| `"data:Int16Array;base64,<data>"`    | `short[]`  | [Int16Array]         |
| `"data:Int32Array;base64,<data>"`    | `int[]`    | [Int32Array]         |
| `"data:BigInt64Array;base64,<data>"` | `long[]`   | [BigInt64Array]      |
| `"data:Float32Array;base64,<data>"`  | `float[]`  | [Float32Array]       |
| `"data:Float64Array;base64,<data>"`  | `double[]` | [Float64Array]       |
| `"foo"`                              | String     | [String]             |

## POJOs
So, `LibJson` will always parse JSON into these types and vice versa. The question is, what is with [POJO]'s?

## Duck-Typing
As the JSON _(and JBON)_ parser do only operate on the above types, there are two ways to add POJO.

## Proxies and symbols
A proxy is a class that implements methods to be used with native data types. It simplifies the usage of the raw in-memory data model. All **complex types** allow to attach proxies, bound using symbols as keys. A symbol is a namespace that is shared by a set of classes and libraries. The base library comes with a default symbol that represents the Naksha namespace. Proxies should use their own namespace to avoid collisions with the Naksha proxies. For example, the `lib-geo` comes out of the box with a data model for Geo-JSON and with an own symbol for all its proxies.

Based upon the in-memory data, multi-platform code can be written that implements APIs and business logic in proxies to be available on all platforms, for example to be used in an `pg_cron` job or background thread in Java/JVM. The big advantage of the separation of in-memory data and algorithms (business logic) is that all code can work with the same data, without the need to convert the data from one layout into another layout.

All proxies should end with the postfix `{name}Proxy`, for example `XyzFeatureProxy`. This simplifies to understand that a class is a proxy, it needs to be attached to a native complex type. All proxies must have a primary constructor that do **not** require any arguments, but can have any number of secondary constructors to create standalone new instances.

## Late binding
Proxies are late bound. For this purpose the parameterless primary constructor is invoked (via reflection), when the proxy is dynamically bound to an existing native object. This situation can be handled by the object through overriding of the `bind` method.

For example, in the Naksha-Hub pipelines all features are exposed as `XyzFeatureProxy` instances. However, these are only proxies created at the underlying in-memory data via `Base.proxy(data, XyzFeatureProxy::class)`. So, when a handler needs values from `properties.@ns:com:here:mom:delta`, these are available through the standard data mode implemented in `XyzFeatureProxy` and can be used directly like `feature.getProperties().getDelta()`.

However, if a handler is part of a custom extension, it may want to access a custom namespace, for example `properties.@ns:com:customData`. The default `XyzFeatureProxy` does not expose it. If the default **XyzFeatureProxy** would be a normal [POJO](https://en.wikipedia.org/wiki/Plain_old_Java_object) the handler would need to first convert the Xyz-Feature into some proprietary object, then it could modify it. However, after doing so, it would have to convert the proprietary object back into the XYZ-Feature for the next handler. This even would require to keep all unknown properties intact and unchanged. The effort to do this is immense and the code will become very slow due to all the data transformations. To solve this problem (and many other), `lib-base` comes with an abstraction between the in memory data storage and the data model. When being based upon `lib-base`, the handler only need to create his own data model, for example:

```kotlin
// Note: Property delegation only works for primitives!
class CustomDataProxy : ObjectProxy() {
    var name: String? by this
    var age: Int? by this
}
class CustomPropertiesProxy : ObjectProxy() {
    var customData: CustomDataProxy
        get() = getOrCreate("@ns:com:customData", CustomDataProxy::class)
        set(value) = set("@ns:com:customData", value)
}
class CustomFeatureProxy : ObjectProxy() {
    var id: String? by this
    override var properties: CustomPropertiesProxy
        get() = getOrCreate("properties", CustomPropertiesProxy::class)
        set(value) = set("properties", value)
}
```

This is not much code (when using [Kotlin](https://kotlinlang.org/)), but it now allows the custom handler to access Xyz-Feature without converting the in memory data. The handler just queries its own proxy. In the above example this would look like:

```kotlin
class CustomHandler : IEventHandler {
    fun processEvent(event: IEvent): Response {
        val request = event.getRequest()
        for (op in request.ops) {
            if (op is FeatureOp) {
                // Apply the own data model
                val feature = op.feature.proxy(CustomFeatureProxy::class)
                if (feature.properties.customData.name == null) {
                    feature.properties.customData.name = "Unknown"
                }
                require(feature.properties.customData.age >= 21) {
                    "The age of the feature ${feature.id} must be greater/equal 21"
                }
            }
        }
        return event.sendUpstream(event)
    }
}
```

As shown, with just around 30 lines of code an own data model can be created and used in an own event-handler to implement some business logic in the Naksha-Hub pipeline, avoiding expensive in-memory transformations and issues. Additionally, this allows to apply general tooling to the data, for example to generate differences, calculate patches and then apply these patches, handling conflicts.

Eventually, when the data need to be written into the database or send to the client, the corresponding encoders can simply apply a raw data model to the in-memory data without any further knowledge about the specific details of the data-model and without transforming the in-memory data. They will simply use the agnostic general model, for example `feature.proxy(AnyMapProxy::class)`. The `AnyMapProxy` basically treats the underlying data object as a plain map (`MutableMap<*,*>`).

## KClass
All proxies require a `KClass`, which is very basic reflection type needed as helper to late create proxies at runtime and to cast children of proxies. In Java there is a helper method to simplify the usage of this Kotlin specific type: `Base.klassFor(SomeJava.class)`.

- [null]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/null
- [Symbol]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol
- [Error]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error
- [POJO]: https://en.wikipedia.org/wiki/Plain_Old_Java_Object
- [Object]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object
- [Map]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map
- [Array]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array
- [Number]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number
- [String]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String
- [BigInt]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt
- [Uint8Array]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array
- [Int16Array]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Int16Array
- [Int32Array]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Int16Array
- [BigInt64Array]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt64Array
- [Float32Array]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Float32Array
- [Float64Array]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Float64Array

