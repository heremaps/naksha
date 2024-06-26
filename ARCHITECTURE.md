# Architecture
This document grants an overview of the modules of the project and the basic architecture.

## Basic layout
The project persists out of modules. The convention is: `{domain}-{department}-{type}-{module}`.

The **domain** is the TLD (top level domain) of the organisation that contributes a module, and normally the one that maintains it. The TLD is written in reverse order notation. For example `here.com` becomes `com.here`. If the domain ends with `.com` this can be left away, therefore `com.here` is shortened to `here`.

The **department** is an arbitrary separation within the organisation, like `naksha` is the Naksha team within the [HERE](https://here.com) organisation.

The **type** is the module type, currently the following types are allowed:
- **app**: For applications that can be run at the console (CLI aka command-line-interface) or desktop.
- **service**: For services installed for example into [systemd](https://en.wikipedia.org/wiki/Systemd) that provide APIs.
- **lib**: For libraries.
- **handler**: For handlers used in the Naksha services.
- **storage**: For storage implementations.
- **data**: For data import/export implementations. These are used to convert incoming files into the common memory model and vice versa.
  - Not yet available (TBD)

## Applications and Services
- [service-hub](here-naksha-app-service): The Naksha-Hub service that offers a REST API. It allows the installed the PLV8 storage into PostgresQL databases and uses the PSQL storage to access the data stores in such databases. Installation of PLV8 is optional, but required to allow manual SQL queries in the database.
- **TBD**: [app-naksha](here-naksha-app-naksha): The Naksha CLI tool that allows to access data at the console. It comes with import and export functions and other useful features.

## Handlers
- [handler-activitylog](here-naksha-handler-activitylog): The activity-log handler, which returns downward compatibility to the activity log. This was a sub-service of Data-Hub that provides access to the change-log of a space. It added JSON patches into the objects and had a slightly different format than the current Naksha history, but can be restored from Naksha default storage history.
- **TBD**: [handler-view](here-naksha-handler-view): A set of handlers to be used with [Naksha-Hub](here-naksha-service-hub) to create views. It comes with a handler that allows to combine spaces and/or collections from storages into a view. It utilizes [lib-view](here-naksha-lib-view) and fakes a storage, when combining spaces.
- **TBD**: [handler-storage](here-naksha-handler-storage): The default storage handler that can interact with all storages implementing `IStorage` and allows to execute requests with them.

## Storages
- **TBD**: [storage-psql](here-naksha-storage-psql): A pure Java library that uses [lib-psql](here-naksha-lib-psql), and implements a connection pool with a `IStorage` in Java. It adds support to optimize read-queries and write-queries by using multiple connections in parallel for bulk processing, when accepting the risks coming with this.
- [storage-http](here-naksha-storage-http): A pure Java library implements of a remote storage, accessed via HTTP.
- **TBD**: [storage-datahub](here-naksha-storage-datahub): A pure Java library that implements a storage using the Data-Hub connector API, which allows to access AWS lambda or HTTP implementations.
- **TBD**: [storage-ims](here-naksha-storage-http): A pure Java library that implements a storage that accesses the **Interactive Map Service** and uses the catalogs as databases with the layers used as collections.

## Libraries
- [lib-view](here-naksha-lib-view): A pure Java library that allows to combine multiple collections into a logical collection. **TODO**: Port to Kotlin to make it working multi-platform.
- [lib-heapcache](here-naksha-lib-heapcache): A pure Java library that allows to cache data on the Java Heap. It can be wrapped around any `IStorage`.
- [lib-base](here-naksha-lib-base): A multi-platform framework written in Kotlin and compiled down to JavaScript, TypeScript and JVM. It allows to write code in Kotlin that then can be used in all these languages. The main purpose is to provide an in-memory data model that is type agnostic, but allows to attach proxies at runtime to gain type-safety. It comes out of the box with JSON serialization and deserialization capabilities.
- [lib-geo](here-naksha-lib-geo): A multi-platform library, based upon **lib-base**, that provides proxies for Geo-JSON and adds support for geometry functions.
- [lib-jbon](here-naksha-lib-jbon): A multi-platform library, based upon **lib-base**, that implements a binary encoding of data. The binaries are immutable and allow live reading. The library comes with a encoder/decoder. The format is optimized to minimize the size of data by using dictionaries to remove duplicates.
- [lib-model](here-naksha-lib-model): A multi-platform library that provides the shared data model, used by the [Naksha-Hub](here-naksha-app-service) and other Naksha libraries. It mainly contains all the core APIs and classes needed across all platforms.
- [lib-core](here-naksha-lib-core): A pure Java library that adds server side code on top of **lib-model**, which is multi-platform.
- [lib-psql](here-naksha-lib-psql): A multi-platform library, that provides an implementation of the request/response model provided by [lib-model](here-naksha-lib-model) using PostgresQL queries. It requires a database connection provider to operate. All classes are prefixed with `Psql`. The library currently works via JDBC and within the PostgresQL database itself, when the [PLV8 JavaScript Extension](https://plv8.github.io/) is installed.
- [lib-auth](here-naksha-lib-auth): A multi-platform library that provides authorization capabilities. It basically simplifies the access checks dramatically and comes with an extended documentation out of the box.

