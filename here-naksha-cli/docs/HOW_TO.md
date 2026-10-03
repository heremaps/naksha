# How to use naksha-cli

## Copy command

### Database population

1. Prepare the source Storage Configuration

   To generate features use Generating Storage. Look at [GeneratingStorageConfig.md](./GeneratingStorageConfig.md) to learn more about configuration.

   Create `gen.json` file. The example content of the file:
   ```json
      {
        "id": "test_generating_storage",
        "className": "com.here.naksha.cli.storages.GeneratingStorage",
        "properties": {
          "featureTemplateFile": "./sample_topology_feature.json",
          "count": 40000,
          "tileIdsCsvFile": "./tile_ids.csv",
          "idsPrefix": "gen"
        }
      }
   ```

2. Prepare the target Storage Configuration

   Create `psql.json` file. The example content of the file:
    ```json
    {
      "id": "storage",
      "type": "Storage",
      "create": true,
      "upgrade": true,
      "className": "naksha.psql.PsqlStorage",
      "master": {
        "host": "0.0.0.0",
        "database": "postgres",
        "port": "5432",
        "user": "postgres",
        "password": "password",
        "readOnly": false
      }
    }
    ```

3. Run the Copy Command
    ```bash
       ./naksha-cli copy \
         --srcStorageConfig gen.json \
         --targetStorageConfig psql.json \
         --targetMapId "targetmapid" \
         --targetCollectionId "targetcolid" \
         --autoCreateTarget
    ```

### The same PsqlStorage as target and source

1. Prepare the Storage Configuration

   Create `test_config.json` file. The example content of the file:
    ```json
    {
      "id": "storage",
      "type": "Storage",
      "create": true,
      "upgrade": true,
      "className": "naksha.psql.PsqlStorage",
      "master": {
        "host": "0.0.0.0",
        "database": "postgres",
        "port": "5432",
        "user": "postgres",
        "password": "password",
        "readOnly": false
      }
    }
    ```
   
2. Run the Copy Command
    ```bash
    ./naksha-cli copy \
      --srcStorageConfig test_config.json \
      --srcMapId "srcmapid" \
      --srcCollectionId "srccolid" \
      --targetStorageConfig test_config.json \
      --targetMapId "targetmapid" \
      --targetCollectionId "targetcolid"
    ```
## Stream copy command (POC)

The `stream-copy` command copies a collection from a source into one or more targets using the streaming API. Sources and targets are providers, discovered using the Java SPI _(`META-INF/services/com.here.naksha.cli.stream.StreamProvider`)_ and selected by name. Additional providers can be added by putting their jars into `here-naksha-cli/plugins/`.

List the available providers:
```bash
./naksha-cli stream-copy --list
```

The POC contains only the two built-in providers:

- `random`: a source that generates random features, every feature in multiple states. It accepts the configuration of the Generating Storage _(`count`, `idsPrefix`, `featureTemplateFile`, `tileIds` or `tileIdsCsvFile`)_, plus `statesPerFeature` _(default `3`)_. Without a configuration it generates 1000 features.
- `null`: a target that acknowledges every chunk and throws the data away. It needs no configuration.

Copy random features into the `null` target:
```bash
./naksha-cli stream-copy --source random --sourceConfig gen.json --target null
```

When a copy aborts, a recovery request is written into `stream-recovery.json` _(see `--recoveryFile`)_, continue with `--resume stream-recovery.json`. See `stream-copy --help` for all options.
