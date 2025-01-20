package com.here.naksha.handler.activitylog

import com.fasterxml.jackson.databind.JsonNode
import naksha.base.AnyObject
import naksha.base.JvmBoxingUtil.box
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.NakshaProperties

class NakshaActivityLog : AnyObject() {
    companion object {
        const val ID = "id"

        private val STRING_NULL = NullableProperty<NakshaActivityLog, String>(String::class)
        private val ORIGINAL = NotNullProperty<NakshaActivityLog, Original>(Original::class)
        private val DIFF_NULL = NullableProperty<NakshaActivityLog, JsonNode>(JsonNode::class)

        fun getActivityLog(properties: NakshaProperties): NakshaActivityLog? = box(
            properties[NakshaProperties.XYZ_ACTIVITY_LOG_NS],
            NakshaActivityLog::class.java
        );
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