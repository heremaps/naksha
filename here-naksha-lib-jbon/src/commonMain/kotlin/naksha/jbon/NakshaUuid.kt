@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.Int64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A helper to manage Naksha UUIDs.
 */
@JsExport
@Deprecated("Please use new class from lib-model", level = DeprecationLevel.WARNING)
class NakshaUuid(val storageId: String, val collectionId: String, val year: Int, val month: Int, val day: Int, val seq: Int64, val uid: Int) {
    private lateinit var string : String
    companion object NakshaUuidCompanion {
        @JvmStatic
        fun fromString(s:String) : NakshaUuid {
            val values = s.split(':')
            check(values.size == 7) { "invalid naksha uuid $s" }
            return NakshaUuid(
                    values[0],
                    values[1],
                    values[2].toInt(),
                    values[3].toInt(),
                    values[4].toInt(),
                    Int64( values[5].toLong()),
                    values[6].toInt(),
            )
        }

        @JvmStatic
        fun from(storageId: String, collectionId: String, txn: Int64, uid: Int): NakshaUuid {
            val nakshaTxn = NakshaTxn(txn)
            return NakshaUuid(
                    storageId,
                    collectionId,
                    nakshaTxn.year(),
                    nakshaTxn.month(),
                    nakshaTxn.day(),
                    nakshaTxn.seq(),
                    uid
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