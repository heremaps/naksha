# How to use naksha-cli

## Copy command

1. Prepare the Storage Configuration

   In my case:
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
        "password": "pass",
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