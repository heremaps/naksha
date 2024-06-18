@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.Int64
import naksha.base.P_JsMap
import naksha.base.P_Map
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class XyzNs : XyzStruct<XyzNs>() {
    private lateinit var createdAt: Int64
    private lateinit var updatedAt: Int64
    private lateinit var txn: NakshaTxn
    private var action: Int = 0
    private var version: Int = 0
    private lateinit var authorTs: Int64

    // Strings and maps are expensive to parse, therefore we only do on demand.
    private var uuid: String = UNDEFINED_STRING
    private var puuid: String? = UNDEFINED_STRING
    private var appId: String = UNDEFINED_STRING
    private var author: String = UNDEFINED_STRING
    private var grid: Int = UNDEFINED_INT

    override fun parseHeader() {
        super.parseXyzHeader(XYZ_NS_VARIANT)

        check(reader.isTimestamp()) { "Field 'createdAt' of XYZ namespace is not timestamp" }
        createdAt = reader.decodeTimestamp()
        check(reader.nextUnit()) { "Failed to move forward to 'updatedAt' field" }
        updatedAt = if (reader.isNull()) createdAt else reader.decodeTimestamp()
        check(reader.nextUnit()) { "Failed to move forward to 'txn' field" }
        txn = NakshaTxn(reader.decodeInt64() ?: throw IllegalStateException("Missing txn"))
        check(reader.nextUnit()) { "Failed to move forward to 'action' field" }
        action = reader.decodeInt32()
        check(reader.nextUnit()) { "Failed to move forward to 'version' field" }
        version = reader.decodeInt32()
        check(reader.nextUnit()) { "Failed to move forward to 'author_ts' field" }
        authorTs = if (reader.isNull()) updatedAt else reader.decodeTimestamp()
        check(reader.nextUnit()) { "Failed to move forward to 'puuid' field" }
    }

    fun createdAt(): Int64 = createdAt
    fun updatedAt(): Int64 = updatedAt
    fun txn(): NakshaTxn = txn
    fun action(): Int = action
    fun actionAsString(): String? = when (action) {
        ACTION_CREATE -> "CREATE"
        ACTION_UPDATE -> "UPDATE"
        ACTION_DELETE -> "DELETE"
        else -> null
    }

    fun version(): Int = version
    fun authorTs(): Int64 = authorTs

    fun puuid(): String? {
        var value = this.puuid
        if (value === UNDEFINED_STRING) {
            reset()
            value = if (reader.isNull()) null else reader.decodeString()
            this.puuid = value
        }
        return value
    }

    fun uuid(): String {
        var value = this.uuid
        if (value === UNDEFINED_STRING) {
            reset()
            reader.nextUnit() // puuid
            value = reader.decodeString()
            this.uuid = value
        }
        return value
    }

    fun appId(): String {
        var value = this.appId
        if (value === UNDEFINED_STRING) {
            reset()
            reader.nextUnit() // puuid
            reader.nextUnit() // uuid
            value = reader.decodeString()
            this.appId = value
        }
        return value
    }

    fun author(): String {
        var value = this.author
        if (value === UNDEFINED_STRING) {
            reset()
            reader.nextUnit() // puuid
            reader.nextUnit() // uuid
            reader.nextUnit() // appId
            value = reader.decodeString()
            this.author = value
        }
        return value
    }

    fun grid(): Int {
        var value = this.grid
        if (value == UNDEFINED_INT) {
            reset()
            reader.nextUnit() // puuid
            reader.nextUnit() // uuid
            reader.nextUnit() // appId
            reader.nextUnit() // author
            value = reader.decodeInt32()
            this.grid = value
        }
        return value
    }

    /**
     * Convert this XYZ namespace into a map. Beware that the transaction-number (txn) will be exposed as string.
     * @param storageId The storage-identifier, this is necessary to expose the transaction-number in a form readable by Javascript clients.
     * @param tags The tags to merge into, if any.
     * @return the XYZ namespace as map.
     */
    fun toIMap(storageId: String, tags: Array<String>?): P_Map<String, *> {
        val map = P_JsMap()
        map["createdAt"] = createdAt().toDouble()
        map["updatedAt"] = updatedAt().toDouble()
        map["txn"] = txn().toUuid(storageId).toString()
        when (action()) {
            ACTION_CREATE -> map["action"] = "CREATE"
            ACTION_UPDATE -> map["action"] = "UPDATE"
            ACTION_DELETE -> map["action"] = "DELETE"
        }
        map["version"] = version()
        map["author_ts"] = authorTs().toDouble()
        if (puuid() != null) map["puuid"] = puuid()
        map["uuid"] = uuid()
        map["author"] = author()
        map["app_id"] = appId()
        map["grid"] = grid()
        if (tags != null) map["tags"] = tags
        return map
    }
}