package naksha.psql

import naksha.model.objects.NakshaCollection

class DeleteFeaturePartitioned : DeleteFeatureBase(collection = NakshaCollection(
    id = "",
    partitions = 4
), mapId = "")