@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.FeatureAttributes
import kotlin.js.JsExport

@JsExport
class ReadFeatures : AccessRightsAction<FeatureAttributes, ReadFeatures>() {

    override val name: String = NAME

    companion object {
        const val NAME = "readFeatures"
    }
}

@JsExport
class CreateFeatures :
    AccessRightsAction<FeatureAttributes, CreateFeatures>() {

    override val name: String = NAME

    companion object {
        const val NAME = "createFeatures"
    }
}

@JsExport
class UpdateFeatures :
    AccessRightsAction<FeatureAttributes, UpdateFeatures>() {

    override val name: String = NAME

    companion object {
        const val NAME = "updateFeatures"
    }
}

@JsExport
class DeleteFeatures :
    AccessRightsAction<FeatureAttributes, DeleteFeatures>() {

    override val name: String = NAME

    companion object {
        const val NAME = "deleteFeatures"
    }
}
