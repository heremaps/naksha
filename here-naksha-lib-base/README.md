# Lib-Base
A multi-platform library that is available in JavaScript, TypeScript and for the Java/JVM. It provides an abstraction of native types.

The core concept is to provide an in-memory data model that is flexible and multi-platform. The base library defines a set of interfaces and some singletons (for example `Base`), being supported by every target platform. The platform grants access to the concrete representations of the abstractions, by providing the singletons with helper methods. This is used to access the data being in memory. The in memory data only persists out of the following basic pieces:

- Complex types:
  - `BaseObject`: A virtual interface implemented by all complex types.
  - `BaseMap`: A platform native map implementation.
  - `BaseList`: A platform native list implementation.
  - `BaseDataView`: A platform native view above a byte-array to perform binary operations.
- Scalar types:
  - `ByteArray`: Kotlin out of the box supported byte-array.
  - `Byte`: Kotlin out of the box supported 8-bit integer.
  - `Short`: Kotlin out of the box supported 16-bit integer.
  - `Int`: Kotlin out of the box supported 32-bit integer.
  - `Int64`: Because Kotlin does not compile **Long** into a native type for JavaScript/TypeScript, the base library introduces the `Int64` interface and relies upon the platform implementation to support a native type.
  - `Bool`: Kotlin out of the box supported.
  - `Double`: Kotlin out of the box supported.
  - `String`: Kotlin out of the box supported.
  - `Symbol`: A symbol that avoids collisions and allows to bind proxies to complex objects.

To be able to support these native types, all interfaces are empty and for each of the interfaces the base library comes with static helper methods to manipulate them, implemented platform specific. Only the multi-platform code is limited to the static methods of `Base`, platform bound code can directly interact with the platform specific implementations, for example in Java [JvmMap](src/jvmMain/kotlin/JvmMap.kt) is implemented as a **LinkedHashMap** and therefore can be used like this, while in JavaScript it is a native [Map](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map) and can be used like this.

## Proxies and symbols
A proxy is a class that implements methods to be used with native data types. It simplifies the usage of the raw in-memory data model. All **complex types** allow to attach proxies, bound using symbols as keys. A symbol is a namespace that is shared by a set of classes and libraries. The base library comes with a default symbol that represents the Naksha namespace. Proxies should use their own namespace to avoid collisions with the Naksha proxies. For example, the `lib-geo` comes out of the box with a data model for Geo-JSON and with an own symbol for all its proxies.

Based upon the in-memory data, multi-platform code can be written that implements APIs and business logic in proxies to be available on all platforms, for example to be used in an `pg_cron` job or background thread in Java/JVM. The big advantage of the separation of in-memory data and algorithms (business logic) is that all code can work with the same data, without the need to convert the data from one layout into another layout.

All proxies should end with the postfix `{name}Proxy`, for example `XyzFeatureProxy`. This simplifies to understand that a class is a proxy, it needs to be attached to a native complex type. All proxies must have a primary constructor that do **not** require any arguments, but can have any number of secondary constructors to create standalone new instances.

## Late binding
Proxies are late bound. For this purpose the parameterless primary constructor is invoked (via reflection), when the proxy is dynamically bound to an existing native object. This situation can be handled by the object through overriding of the `bind` method.

For example, in the Naksha-Hub pipelines all features are exposed as `XyzFeatureProxy` instances. However, these are only proxies created at the underlying in-memory data via `Base.proxy(data, XyzFeatureProxy::class)`. So, when a handler needs values from `properties.@ns:com:here:delta`, these are available through the standard data mode implemented in `XyzFeatureProxy` and can be used directly like `feature.getProperties().getDelta()`.

However, if a handler is part of a custom extension, it may want to access a custom namespace, for example `properties.@ns:com:customData`. The default `XyzFeatureProxy` does not expose it. If the default **XyzFeatureProxy** would be a normal [POJO](https://en.wikipedia.org/wiki/Plain_old_Java_object) the handle would need to first convert the Xyz-Feature into some proprietary object, then it could modify it. However, after doing so, it would have to convert the proprietary object back into the XYZ-Feature for the next handler. This even would require to keep all unknown properties intact and unchanged. The effort to do this is immense and the code will become very slow due to all the data transformations. To solve this problem (and many other), `lib-base` comes with an abstraction between the in memory data storage and the data model. When being based upon `lib-base`, the handle only need to create his own data model, for example:

```kotlin
class CustomDataProxy : BaseObjectProxy() {
    var name: String?
        get() = getOrNull("name", String::class)
        set(value: String?) = set("name", value)
}
class CustomPropertiesProxy : XyzProperties() {
    var customData: CustomDataProxy
        get() = getOrCreate("@ns:com:customData", CustomDataProxy::class)
        set(value) = set("@ns:com:customData", value)
}
class CustomFeatureProxy : XyzFeatureProxy() {
    override var properties: CustomPropertiesProxy
        get() = getOrCreate("properties", CustomPropertiesProxy::class)
        set(value) = super.set(value)
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
            }
        }
        return event.sendUpstream(event)
    }
}
```

As shown, with just around 30 lines of code an own data model can be created and used in an own event-handler in the Naksha-Hub pipeline, avoiding expensive in-memory transformations and issues. Additionally, this allows to apply general tooling to the data, for example to generate differences, calculate patches and then apply these patches, handling conflicts.

Eventually, when the data need to be written into the database or send to the client, the corresponding encoders can simply apply a raw data model to the in-memory data without any further knowledge about the specific details of the data-model and without transforming the in-memory data. They will simply use the agnostic general model, for example `feature.proxy(AnyMapProxy::class)`. The `AnyMapProxy` basically treats the underlying data object as a plain map (`MutableMap<*,*>`).

## KClass
All proxies require a `KClass`, which is very basic reflection type needed as helper to late create proxies at runtime and to cast children of proxies. In Java there is a helper method to simplify the usage of this Kotlin specific type: `Base.klassFor(SomeJava.class)`.
