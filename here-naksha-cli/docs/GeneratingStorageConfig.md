# Generating Storage Config

We define the configuration in a JSON file. The schema looks like this:

```json
{
  "id": "generating_storage",
  "className": "com.here.naksha.cli.storages.GeneratingStorage",
  "properties": {
    // Required: Number of features to generate
    "count": 1000,
    // Optional: Prefix for generated feature IDs. Defaults to "gen" if not provided
    "idsPrefix": "genTest",
    // Optional: The path to the feature template file used as a base for generating features
    // It can be an absolute path or relative to the working directory
    "featureTemplateFile": "sample_topology_feature.json",
    // Either "tileIdsCsvFile" or "tileIds" must be provided to specify the tiles where features will be generated
    // Absolute path or relative to the working directory
    "tileIdsCsvFile": "tile_ids.csv",
    "tileIds": [
      122013000000,
      122013000001
    ]
  }
}
```