@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A helper to manage Naksha UUIDs.
 */
@JsExport
class NakshaUuid(val storageId: String, val collectionId: String, val year: Int, val month: Int, val day: Int, val seq:BigInt64, val uid: Int) {
    private lateinit var string : String
    companion object {
        fun fromString(s:String) : NakshaUuid {
            TODO("Parse the string")
        }
    }

    override fun equals(other: Any?) : Boolean {
        if (other is NakshaUuid) return this.toString() == other.toString()
        if (other is String) return this.toString() == other
        return false
    }

    override fun hashCode() : Int = toString().hashCode()

    override fun toString() : String {
        if (!this::string.isInitialized) string = "$storageId:$collectionId:$year:$month:$day:$seq:$uid"
        return string
    }
}