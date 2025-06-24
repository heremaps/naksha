package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode

class HistoryPuuidTest: PgTestBase(NakshaCollection(
    id = "history_puuid_test_collection",
    storeHistory = StoreMode.ON,
    storeDeleted = StoreMode.ON
)) {

    fun
}