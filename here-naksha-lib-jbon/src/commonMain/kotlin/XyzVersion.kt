@file:OptIn(ExperimentalJsExport::class)
package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class XyzVersion(val major: Int, val minor: Int, val revision: Int) : Comparable<XyzVersion> {
    companion object {
        fun fromString(s: String): XyzVersion {
            val majorEnd: Int = s.indexOf('.')
            // "5" -> 5,0,0
            if (majorEnd < 0) return XyzVersion(s.toInt(), 0, 0)
            val minorEnd: Int = s.indexOf('.', majorEnd + 1)
            // "5.1" -> 5,1,0
            if (minorEnd < 0) return XyzVersion(
                    s.substring(0, majorEnd).toInt(),
                    s.substring(majorEnd + 1).toInt(),
                    0
            )
            // "5.1.2" -> 5,1,2
            return XyzVersion(
                    s.substring(0, majorEnd).toInt(),
                    s.substring(majorEnd + 1, minorEnd).toInt(),
                    s.substring(minorEnd + 1).toInt())
        }

        fun fromBigInt(v: BigInt64) : XyzVersion {
            val major = (v ushr 32).toInt()
            val minor = (v.toInt() ushr 16) and 0xffff
            val revision = v.toInt() and 0xffff
            return XyzVersion(major, minor, revision)
        }
    }

    fun toBigInt() : BigInt64 {
        return (BigInt64(major) shl 32) or BigInt64(minor shl 16) or BigInt64(revision)
    }

    override fun compareTo(other: XyzVersion): Int {
        var d = major - other.major
        if (d != 0) return d
        d = minor - other.minor
        if (d != 0) return d
        return revision - other.revision
    }

    override fun equals(other: Any?) : Boolean {
        return if (other is XyzVersion) major == other.major && minor == other.minor && revision == other.revision else false
    }

    override fun hashCode() : Int {
        return (major shl 16) or minor
    }

    override fun toString() : String {
        return "$major.$minor.$revision"
    }
}