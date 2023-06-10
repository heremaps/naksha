![Naksha (नक्शा) - XYZ-Hub](xyz.svg)
---

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

[Naksha](https://en.wikipedia.org/wiki/Naksha) [(नक्शा)](https://www.shabdkosh.com/search-dictionary?lc=hi&sl=en&tl=hi&e=%E0%A4%A8%E0%A4%95%E0%A5%8D%E0%A4%B6%E0%A4%BE) is the name of this fork of the [XYZ-Hub](https://github.com/heremaps/xyz-hub) (pronounced **nakshaa** or **nakśā**). It stays a web service for the access and management of geospatial data. This spin-off was done to independently realize needed new features, not planned to be supported in the original [XYZ-Hub](https://github.com/heremaps/xyz-hub) project.

The meaning of [Naksha](https://en.wikipedia.org/wiki/Naksha) is “Map”.

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Getting Started](#3-getting-started)
4. [Usage](#4-usage)
5. [Acknowledgements](#5-acknowledgements)
6. [Contributing](#6-contributing)
7. [License](#license)

---

# 1. Overview
Naksha features are:
* Organize geo datasets in _spaces_
* Store and manipulate individual geo features (points, linestrings, polygons)
* Retrieve geo features as vector tiles, with or without clipped geometries
* Search for geo features spatially using a bounding box, radius, or any custom geometry
* Explore geo features by filtering property values
* Retrieve statistics for your _spaces_
* Analytical representation of geo data as hexbins with statistical information
* Connect with different data sources
* Build a real-time geodata pipeline with processors
* Attach listeners to react on events

Naksha uses [GeoJSON](https://tools.ietf.org/html/rfc79460) as the main geospatial data exchange format. Tiled data can also be provided as [MVT](https://github.com/mapbox/vector-tile-spec/blob/master/2.1/README.md).

---

# 2. Prerequisites

* Java 8+
* Maven 3.6+
* Postgres 10+ with PostGIS 2.5+
* Redis 5+ (optional)
* Docker 18+ (optional)
* Docker Compose 1.24+ (optional)

---

# 3. Getting started

Clone and install the project using:

```bash
git clone https://github.com/heremaps/xyz-hub.git
cd xyz-hub
mvn clean install
```

### 3.1 With docker

The service and all dependencies could be started locally using Docker compose.
```bash
docker-compose up -d
```

Alternatively, you can start freshly from the sources by using this command after cloning the project:
```bash
mvn clean install -Pdocker
```

*Hint: Postgres with PostGIS will be automatically started if you use 'docker-compose up -d' to start the service.*

### 3.2 Without docker

The service could also be started directly as a fat jar. In this case Postgres and the other optional dependencies need to be started separately.

```bash
java -server -cp xyz-hub-service/target/xyz-hub-service.jar com.here.xyz.hub.Service
```

### 3.3 Configuration

There are two ways to provide runtime configuration:
1. Using custom `config.json` file
2. Using environment variables

#### 3.3.1 Using Custom Config file

The service persists out of modules with a bootstrap code to start the service. All configuration is done in the [config.json](./xyz-hub-service/src/main/resources/config.json).

The bootstrap code could be used to run only the `hub-verticle` or only the `connector-verticle` or it can be used to run both as a single monolith. In a microservice deployment you run one cluster with only `hub-verticle` deployment and another cluster with only `connector-verticle` deployment. It is as well possible to mix this, so running a monolith deployment that optionally can use connector configurations to use foreign connectors for individual spaces.

**Warning**: The `connector-verticle` does not perform security checks, so open it to external access will bypass all security restrictions!

The location of the configuration file could be modified using environment variables or by creating the `config.json` file in the corresponding configuration folder. The exact configuration folder is platform dependent, but generally follows the [XGD user configuration directory](https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html), standard, so on Linux being by default `~/.config/xyz-hub/`. For Windows the files will reside in the [CSIDL_PROFILE](https://learn.microsoft.com/en-us/windows/win32/shell/csidl?redirectedfrom=MSDN) folder, by default `C:\Users\{username}\.config\xyz-hub`. This path could be changed via environment variable `XDG_CONFIG_HOME`, which will result in the location `$XDG_CONFIG_HOME/xyz-hub/`. Next to this, an explicit location can be specified via the environment variable `XYZ_CONFIG_PATH`, this path will not be extended by the `xyz-hub` folder, so you can directly specify where to keep the config files. This is important when you want to start multiple versions of the service: `XYZ_CONFIG_PATH=~/.config/xyz-hub/a/ java -jar xyz-hub-service.jar`.

```bash
# Create copy of default config file
mkdir ~/.config/xyz-hub
cp xyz-hub-service/src/main/resources/config.json ~/.config/xyz-hub/

# Modify custom file as per need
vi ~/.config/xyz-hub/config.json

# Then, start the service (usual command)
java -server -cp xyz-hub-service/target/xyz-hub-service.jar com.here.xyz.hub.Service
```

#### 3.3.2 Using Environment variables

Environment variables can be explicitly set to override default parameters defined in [config.json](./xyz-hub-service/src/main/resources/config.json) file.

For example:

```shell
# Set environment variables with custom values
export HTTP_PORT=7080
export STORAGE_DB_URL=jdbc:postgresql://localhost:5432/postgres
export STORAGE_DB_USER=postgres_user
export STORAGE_DB_PASSWORD=postgres_pswd
# (optional) below parameters required only for SNS publishing
export ENABLE_TXN_PUBLISHER=true
export AWS_ACCESS_KEY_ID=aws-user-key
export AWS_SECRET_ACCESS_KEY=aws-user-secret
export AWS_DEFAULT_REGION=us-east-1

# Then, start the service (usual command)
java -server -cp xyz-hub-service/target/xyz-hub-service.jar com.here.xyz.hub.Service
```

---

# 4. Usage

Start using the service by creating a _space_:

```bash
curl -H "content-type:application/json" \
-d '{"id": "test-space", "title": "my first space", "description": "my first geodata repo"}' \
"http://localhost:8080/hub/spaces"
```

The service will respond with the space definition including the space ID (should you not specify an own `id`):

```json
{
    "id": "test-space",
    "title": "my first space",
    "description": "my first geodata repo",
    "storage": {
        "id": "psql",
        "params": null
    },
    "owner": "ANONYMOUS",
    "createdAt": 1576601166681,
    "updatedAt": 1576601166681,
    "contentUpdatedAt": 1576601166681,
    "autoCacheProfile": {
        "browserTTL": 0,
        "cdnTTL": 0,
        "serviceTTL": 0
    }
}
```

You can now add _features_ to your brand new space:
```bash
curl -H "content-type:application/geo+json" -d '{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-2.960847,53.430828]},"properties":{"name":"Anfield","@ns:com:here:xyz":{"tags":["football","stadium"]},"amenity":"Football Stadium","capacity":54074,"description":"Home of Liverpool Football Club"}}]}' http://localhost:8080/hub/spaces/pvhQepar/features
```

The service will respond with the inserted geo features:
```json
{
    "type": "FeatureCollection",
    "etag": "b67016e5dcabbd5f76b0719d75c84424",
    "features": [
        {
            "type": "Feature",
            "id": "nf36KMsQAUYoM5kM",
            "geometry": {
                "type": "Point",
                "coordinates": [ -2.960847, 53.430828 ]
            },
            "properties": {
                "@ns:com:here:xyz": {
                    "space": "pvhQepar",
                    "createdAt": 1576602412218,
                    "updatedAt": 1576602412218,
                    "tags": [ "football", "stadium" ]
                },
                "amenity": "Football Stadium",
                "name": "Anfield",
                "description": "Home of Liverpool Football Club",
                "capacity": 54074
            }
        }
    ],
    "inserted": [
        "nf36KMsQAUYoM5kM"
    ]
}
```

### 4.1 OpenAPI specification

The OpenAPI specification files are accessible under the following URIs:
* Full: [http://{host}:{port}/hub/static/openapi/full.yaml](http://localhost:8080/hub/static/openapi/full.yaml)
* Stable: [http://{host}:{port}/hub/static/openapi/stable.yaml](http://localhost:8080/hub/static/openapi/stable.yaml)
* Experimental: [http://{host}:{port}/hub/static/openapi/experimental.yaml](http://localhost:8080/hub/static/openapi/experimental.yaml)
* Contract: [http://{host}:{port}/hub/static/openapi/contract.yaml](http://localhost:8080/hub/static/openapi/contract.yaml)
* Connector: [http://{host}:{port}/psql/static/openapi/openapi-http-connector.yaml](http://localhost:8080/psql/static/openapi/openapi-http-connector.yaml)

---

# 5. Acknowledgements

Naksha (XYZ Hub) uses:

* [Vertx](http://vertx.io/)
* [Geotools](https://github.com/geotools/geotools)
* [JTS](https://github.com/locationtech/jts)
* [Jackson](https://github.com/FasterXML/jackson)
* [AWS SDK](https://aws.amazon.com/sdk-for-java/)

and [others](./pom.xml#L198-L540).

# 6. Contributing

Your contributions are always welcome! Please have a look at the [contribution guidelines](CONTRIBUTING.md) first.

# License
Copyright (C) 2017-2022 HERE Europe B.V.

This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](./LICENSE) file for details.