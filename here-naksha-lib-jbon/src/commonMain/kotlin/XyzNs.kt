@file:OptIn(ExperimentalJsExport::class)
package com.here.naksha.lib.jbon

import com.here.naksha.lib.jbon.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class XyzNs : XyzSpecial<XyzNs>() {
    private lateinit var createdAt: BigInt64
    private lateinit var updatedAt: BigInt64
    private lateinit var txn: NakshaTxn
    private var action: Int = 0
    private var version: Int = 0
    private lateinit var authorTs: BigInt64
    private lateinit var extend: BigInt64

    // Strings and maps are expensive to parse, therefore we only do on demand.
    private var uuid: String = UNDEFINED_STRING
    private var puuid: String? = UNDEFINED_STRING
    private var appId: String = UNDEFINED_STRING
    private var author: String = UNDEFINED_STRING
    private var crid: String? = UNDEFINED_STRING
    private var grid: String = UNDEFINED_STRING

    override fun parseHeader(mandatory: Boolean) {
        super.parseHeader(mandatory)
        check(variant == XYZ_NS)

        createdAt = reader.readTimestamp()
        check(reader.nextUnit())
        updatedAt = if (reader.isNull()) createdAt else reader.readTimestamp()
        check(reader.nextUnit())
        txn = NakshaTxn(reader.readInt64())
        check(reader.nextUnit())
        action = reader.readInt32()
        check(reader.nextUnit())
        version = reader.readInt32()
        check(reader.nextUnit())
        authorTs = reader.readTimestamp()
        check(reader.nextUnit())
        extend = reader.readInt64()
        check(reader.nextUnit())

        setContent(reader.offset, reader.useView().getSize())
    }

    fun createdAt(): BigInt64 = createdAt
    fun updatedAt(): BigInt64 = updatedAt
    fun txn(): NakshaTxn = txn
    fun action(): Int = action
    fun actionAsString() : String? = when(action) {
        ACTION_CREATE -> "CREATE"
        ACTION_UPDATE -> "UPDATE"
        ACTION_DELETE -> "DELETE"
        else -> null
    }
    fun version(): Int = version
    fun authorTs(): BigInt64 = authorTs
    fun extend(): BigInt64 = extend

    fun puuid(): String? {
        var value = this.puuid
        if (value === UNDEFINED_STRING) {
            reset()
            value = if (reader.isNull()) null else reader.readString()
            this.puuid = value
        }
        return value
    }

    fun uuid(): String {
        var value = this.uuid
        if (value === UNDEFINED_STRING) {
            reset()
            reader.nextUnit() // puuid
            value = reader.readString()
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
            value = reader.readString()
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
            value = reader.readString()
            this.author = value
        }
        return value
    }

    fun crid(): String? {
        var value = this.crid
        if (value === UNDEFINED_STRING) {
            reset()
            reader.nextUnit() // puuid
            reader.nextUnit() // uuid
            reader.nextUnit() // appId
            reader.nextUnit() // author
            value = if (reader.isNull()) null else reader.readString()
            this.crid = value
        }
        return value
    }

    fun grid(): String {
        var value = this.grid
        if (value === UNDEFINED_STRING) {
            reset()
            reader.nextUnit() // puuid
            reader.nextUnit() // uuid
            reader.nextUnit() // appId
            reader.nextUnit() // author
            reader.nextUnit() // crid
            value = reader.readString()
            this.grid = value
        }
        return value
    }

    fun mrid() : String = crid() ?: mrid()

    /**
     * Convert this XYZ namespace into a map. Beware that the transaction-number (txn) will be exposed as string.
     * @param storageId The storage-identifier, this is necessary to expose the transaction-number in a form readable by Javascript clients.
     * @param tags The tags to merge into, if any.
     * @return the XYZ namespace as map.
     */
    fun toIMap(storageId: String, tags: IMap?): IMap {
        val map = newMap()
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
        map["extend"] = extend().toDouble()
        if (puuid() != null) map["puuid"] = puuid()
        map["uuid"] = uuid()
        map["author"] = author()
        map["app_id"] = appId()
        if (crid() != null) map["crid"] = crid()
        map["grid"] = grid()
        if (tags != null) map["tags"] = tags
        return map
    }
}