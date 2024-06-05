package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.XyzFeatureAttributes

class ReadFeatures: AccessAction<XyzFeatureAttributes>(READ_FEATURES_ACTION_NAME){

    companion object {
        const val READ_FEATURES_ACTION_NAME = "readFeatures"
    }
}

class CreateFeatures: AccessAction<XyzFeatureAttributes>(CREATE_FEATURES_ACTION_NAME){

    companion object {
        const val CREATE_FEATURES_ACTION_NAME = "createFeatures"
    }
}

class UpdateFeatures: AccessAction<XyzFeatureAttributes>(UPDATE_FEATURES_ACTION_NAME){

    companion object {
        const val UPDATE_FEATURES_ACTION_NAME = "updateFeatures"
    }
}

class DeleteFeatures: AccessAction<XyzFeatureAttributes>(DELETE_FEATURES_ACTION_NAME){

    companion object {
        const val DELETE_FEATURES_ACTION_NAME = "deleteFeatures"
    }
}
