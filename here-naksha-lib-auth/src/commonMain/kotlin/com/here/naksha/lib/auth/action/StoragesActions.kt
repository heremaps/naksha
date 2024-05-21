package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.AccessRightsMatrix
import com.here.naksha.lib.auth.AccessServiceMatrix
import com.here.naksha.lib.auth.attribute.StorageAttributes

class UseStorages: AccessAction<StorageAttributes>(USE_STORAGES_ACTION_NAME){
    companion object {
        const val USE_STORAGES_ACTION_NAME = "useStorages"
    }
}

class ManageStorages: AccessAction<StorageAttributes>(MANAGE_STORAGE_ACTION_NAME){
    companion object {
        const val MANAGE_STORAGE_ACTION_NAME = "manageStorages"
    }
}
