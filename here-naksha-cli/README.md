# Naksha CLI - Command Line Interface for Naksha

## How to Build and Run

Use the provided script `./naksha-cli` to build and run the CLI.

> [!NOTE]
> The build process runs only once by default.  
> To force a rebuild, pass the `--Sbuild` flag:
> ```bash
> ./naksha-cli --Sbuild
> ```

## Available Storages

The fat JAR includes the following built-in storage implementations:

- `com.here.naksha.cli.storages.GeneratingStorage`
- `naksha.psql.PsqlStorage`

### Adding Custom Storage

You can add your own storage implementation by:

1. Creating a class that implements the required storage interface `naksha.model.IStorage`.
2. Ensuring the class is **packaged inside the fat JAR** so it's available at runtime (i.e., included in the JAR's
   classpath).
3. Editing the [Gradle build file](./build.gradle.kts) to include your custom storage class in the final JAR.

> [!NOTE]
> Your custom storage class **must be included in the classpath of the fat JAR** in order to be discovered and loaded
> correctly at runtime.

## Populated PostgreSQL

This module contains a prepared Dockerfile that utilizes the Naksha CLI to create a Docker image with a PostgreSQL
database populated with random features. For more details, see the [docker](./docker/README.md).

## Releasing

To release a new version, use the command below. The JAR file will be prepared automatically.  
The JAR can be found in the [build/libs](./build/libs) directory.

```bash
gradle releaseAndShadow -Prelease.forceVersion=0.1.1
```

## Read More

For more information, see the [docs](./docs).