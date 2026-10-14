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

## Streaming (POC)

These commands copy collections using the streaming API, see [STREAMING_CLI.md](../docs/drafts/STREAMING_CLI.md). Storages and streams are configured once, by answering some questions, and stored in `~/.config/naksha/cli/` _(change it with the environment variable `NAKSHA_CLI_HOME`)_. The POC only contains the built-in providers `null` and `random`.

| Command                             | Description                                                                                             |
|-------------------------------------|---------------------------------------------------------------------------------------------------------|
| `naksha-cli provider list`          | Lists all providers with name and description.                                                         |
| `naksha-cli config create`          | Asks for a name and a provider, runs the questions of the provider, and saves the configuration.        |
| `naksha-cli config list`            | Lists the configured storages.                                                                          |
| `naksha-cli config delete NAME`     | Deletes a configured storage.                                                                           |
| `naksha-cli stream create`          | Asks for a name, the source, the targets and the options, and saves the stream.                        |
| `naksha-cli stream list`            | Lists all streams and their state.                                                                      |
| `naksha-cli stream delete NAME`     | Deletes a stream.                                                                                       |
| `naksha-cli sync NAME`              | Runs the stream from the beginning.                                                                     |
| `naksha-cli sync NAME --continue`   | Continues the stream from the latest recovery request, for example after an abort or to read new data. |

`config create` and `stream create` accept `--name`, `config create` as well `--provider`, everything else is asked.

Copy random features into the `null` target:

```
$ ./naksha-cli config create --name random --provider random
Saved random.
$ ./naksha-cli config create --name null --provider null
Saved null.
$ ./naksha-cli stream create --name random-to-null
Source storage, one of: null, random> random
Source catalog [default]>
Source collection [default]>
Target storage, one of: null, random> null
Target catalog [default]>
Target collection [default]>
Add another target? (y/n), one of: y, n [n]>
Copy the history? (y/n), one of: y, n [y]>
Copy deleted features? (y/n), one of: y, n [y]>
Keep the transactions of the source? (y/n), one of: y, n [y]>
Chunk size [1000]>
Saved random-to-null.
$ ./naksha-cli sync random-to-null
acknowledged 3000, in flight 0, estimated 3000
Success! Copied 3000 tuples.
```

The state of a stream is saved while it runs. When it is interrupted using `Ctrl-C`, or after a crash, continue it with `--continue`.

The `random` storage generates 1000 features with 3 states each. To change this, edit its configuration file, it accepts the configuration of the Generating Storage _(`count`, `idsPrefix`, `featureTemplateFile`, `tileIds` or `tileIdsCsvFile`)_, plus `statesPerFeature`:

```json
{
  "provider": "random",
  "config": {
    "count": 100000,
    "statesPerFeature": 5
  }
}
```

Additional providers can be added by putting their jars into `here-naksha-cli/plugins/`, they are registered in `META-INF/services/naksha.model.IStorageProvider`.

## Populated PostgreSQL

This module contains a prepared Dockerfile that utilizes the Naksha CLI to create a Docker image with a PostgreSQL
database populated with random features. For more details, see the [docker](./docker/README.md).

## Releasing

To release a new version, use the command below. The JAR file will be prepared automatically.  
The JAR can be found in the [build/libs](./build/libs) directory.

```bash
./../gradlew :here-naksha-cli:releaseAndShadow -Prelease.forceVersion=0.1.1
```

## Read More

For more information, see the [docs](./docs).