# Lib-Base
A multi-platform library that is available in JavaScript, TypeScript and for the Java/JVM. It provides an abstraction of native types.

The core concept is to provide an in-memory data model that is flexible and multi-platform. The base library defines a set of interfaces and one singleton (`Base`) that is supported by every supported platform. The platform grants access to the concrete representations of the abstractions, by providing the central static singleton with helper methods. This is used to access the data being in memory. The in memory data only persists out of the following basic pieces:

- Complex types:
  - `IAny`: A virtual interface implemented by all complex types.
  - `IMap`: A platform native map implementation.
  - `IList`: A platform native list implementation.
  - `IBuffer`: A platform native view above a byte-array to perform binary operations.
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

To be able to support these native types, all interfaces are empty and for each of the interfaces the base library comes with static helper methods to manipulate them, implemented platform specific. Only the multi-platform code is limited to the static methods of `Base`, platform bound code can directly interact with the platform specific implementations, for example in Java `JvmMap` is implemented as a **LinkedHashMap** and therefore can be used like this, while in JavaScript it is a plain **Object** and can be used like this.

## Proxies and symbols
A proxy is a class that implements methods to be used with native data types. It simplifies the usage of the raw in-memory data model. All **complex types** allow to attach proxies using symbols as keys. A symbol is a namespace that is shared by a set of classes and libraries. The base library comes with a default symbol that represents the Naksha namespace. Proxies should use their own namespace to avoid collisions with the Naksha proxies. For example, the `lib-geo` comes out of the box with a data model for Geo-JSON and with an own symbol for all its proxies.

Based upon the in-memory data, multi-platform code can be written that implements APIs and business logic in proxies to be available on all platforms, for example to be used in an `pg_cron` job or background thread in Java/JVM. The big advantage of the separation of in-memory data and algorithms (business logic) is that all code can work with the same data, without the need to convert the data from one layout into another layout.

All proxies should start with an upper-cases `P_`, for example `P_Feature`. This simplifies to understand that a class is just a proxy and can't be used standalone, it needs to be attached to a native complex type. All proxies must have only one constructor that accepts variable arguments (between 0 and n). It must at least support a constructor with exactly one argument being `IAny`, which then binds the proxy to this data object. If no argument given, a new empty data object must be created. So, the underlying data node is either `IMap`, `IList` or `IBuffer`.

If constructors should be available for proxies, they must be provided as static methods, for example `P_Point.create(x, y)`.

Proxies are late bound, for example, in the Naksha-Hub pipelines one handler may need values from `properties.@ns:com:here:delta` and will use the corresponding proxy to access it: `Base.proxy(properties["@ns:com:here:delta"], P_Delta.klass)`. The next handler may want to access `properties.@ns:com:here:meta` and can do so without any change to the in-memory data via: `Base.proxy(properties["@ns:com:here:meta"], P_Meta.klass)`. Eventually, when the data need to be written into the database or send to the client, the corresponding encoders can transform the in-memory data without any further knowledge about the specific details of the data-model.

All proxies require a `Klass`, which is very basic reflection type needed as helper to late create proxies at runtime and to cast children of proxies.
