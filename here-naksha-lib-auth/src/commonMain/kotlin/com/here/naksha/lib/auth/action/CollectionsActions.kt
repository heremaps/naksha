package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.AccessAttributeMap
import com.here.naksha.lib.auth.attribute.XyzCollectionAttributes

class ReadCollections : AccessAction<XyzCollectionAttributes>(READ_COLLECTIONS_ACTION_NAME) {

    companion object {
        const val READ_COLLECTIONS_ACTION_NAME = "readCollections"
    }
}

class CreateCollections : AccessAction<XyzCollectionAttributes>(CREATE_COLLECTIONS_ACTION_NAME) {

    companion object {
        const val CREATE_COLLECTIONS_ACTION_NAME = "createCollections"
    }
}

class UpdateCollections : AccessAction<XyzCollectionAttributes>(UPDATE_COLLECTIONS_ACTION_NAME) {

    companion object {
        const val UPDATE_COLLECTIONS_ACTION_NAME = "updateCollections"
    }
}

class DeleteCollections : AccessAction<XyzCollectionAttributes>(DELETE_COLLECTIONS_ACTION_NAME) {

    companion object {
        const val DELETE_COLLECTIONS_ACTION_NAME = "deleteCollections"
    }
}
