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
            val values = s.split(':')
            check(values.size == 7) { "invalid naksha uuid $s" }
            return NakshaUuid(
                    values[0],
                    values[1],
                    values[2].toInt(),
                    values[3].toInt(),
                    values[4].toInt(),
                    BigInt64( values[5].toLong()),
                    values[6].toInt(),
            )
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