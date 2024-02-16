@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class XyzNs : XyzSpecial<XyzNs>() {
    private lateinit var createdAt: BigInt64
    private lateinit var updatedAt: BigInt64
    private lateinit var txn: BigInt64
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
        txn = reader.readInt64()
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
    fun txn(): BigInt64 = txn
    fun action(): Int = action
    fun version(): Int = version
    fun authorTs(): BigInt64 = authorTs
    fun extend(): BigInt64 = extend

    fun puuid() : String? {
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

    fun appId() : String {
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

    fun author() : String {
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

    fun crid() : String? {
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

    fun grid() : String {
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

    /**
     * Convert this XYZ namespace into a map.
     * @param tags The tags to merge into, if any.
     * @return the XYZ namespace as map.
     */
    fun toIMap(tags:XyzTags?) : IMap {
        TODO("Implement me!")
    }
}