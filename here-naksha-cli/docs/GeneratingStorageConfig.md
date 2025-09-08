# Generating Storage Config

We define the configuration in a JSON file. The schema looks like this:

```json
{
  "id": "generating_storage",
  "className": "com.here.naksha.cli.storages.GeneratingStorage",
  "properties": {
    "count": 1000, // Required: Number of features to generate
    "idsPrefix": "genTest", // Optional: Prefix for generated feature IDs. Defaults to "gen" if not provided
    "featureTemplateFile": "sample_topology_feature.json", // Optional: Path to the feature template file used as a base for generated features.
    // Either "tileIdsCsvFile" or "tileIds" must be provided to specify the tiles where features will be generated
    "tileIdsCsvFile": "tile_ids.csv",
    "tileIds": [122013000000, 122013000001]
  }
}
```