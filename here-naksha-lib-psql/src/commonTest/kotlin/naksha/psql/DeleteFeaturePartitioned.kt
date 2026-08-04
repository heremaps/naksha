package naksha.psql

import naksha.model.objects.NakshaCollection

class DeleteFeaturePartitioned : DeleteFeatureBase(collection = NakshaCollection().withPartitions(4))