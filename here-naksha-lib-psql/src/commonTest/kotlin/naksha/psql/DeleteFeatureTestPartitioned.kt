package naksha.psql

import naksha.model.objects.NakshaCollection

class DeleteFeatureTestPartitioned : DeleteFeatureTest(collection = NakshaCollection(
    id = "",
    partitions = 4
))