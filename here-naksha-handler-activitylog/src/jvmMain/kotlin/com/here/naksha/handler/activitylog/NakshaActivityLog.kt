package com.here.naksha.handler.activitylog

import com.fasterxml.jackson.databind.JsonNode
import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.String_TYPE
import naksha.model.objects.NakshaProperties
import naksha.mom.v2.MomProperties

class NakshaActivityLog : AnyObject() {
    companion object {
        @JvmField
        val TYPE = forKClass(NakshaActivityLog::class).withPackageName("com.here.naksha.handler.activitylog")

        const val ID = "id"

        private val STRING_NULL = NullableProperty<NakshaActivityLog, String>(String_TYPE)
        private val ORIGINAL = NotNullProperty<NakshaActivityLog, Original>(Original.TYPE)
        private val DIFF_NULL = NullableProperty<NakshaActivityLog, JsonNode>(forKClass(JsonNode::class))

        @JvmStatic
        fun getActivityLog(properties: NakshaProperties): NakshaActivityLog?
            = properties.getAs(MomProperties.XYZ_ACTIVITY_LOG_NS, TYPE)
    }

    /** The space ID the feature belongs to. */
    var id by STRING_NULL

    /**
     * The operation that lead to the current state of the namespace. Should be a value from {
     * [Action].
     */
    var action by STRING_NULL

    /** The Original tag. */
    var original by ORIGINAL

    /** The Difference tag. */
    var diff by DIFF_NULL
}