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