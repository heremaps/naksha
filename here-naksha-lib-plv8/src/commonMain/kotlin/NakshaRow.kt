@file:Suppress("NOTHING_TO_INLINE")

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*

/**
 * Naksha database rows.
 */

internal inline fun IMap.hasTxn() : Boolean = getAny(COL_TXN) is BigInt64
internal inline fun IMap.getTxn() : BigInt64? = this[COL_TXN]
internal inline fun IMap.setTxn(txn: BigInt64?) { this[COL_TXN] = txn }

internal inline fun IMap.hasTxnNext() : Boolean = getAny(COL_TXN_NEXT) is BigInt64
internal inline fun IMap.getTxnNext(): BigInt64? = this[COL_TXN_NEXT]
internal inline fun IMap.setTxnNext(txnNext : BigInt64?) { this[COL_TXN_NEXT] = txnNext }

internal inline fun IMap.hasUid() : Boolean = getAny(COL_UID) is Int
internal inline fun IMap.getUid() : Int? = this[COL_UID]
internal inline fun IMap.setUid(uid:Int?) { this[COL_UID] = uid }

internal inline fun IMap.hasPTxn() : Boolean = getAny(COL_PTXN) is BigInt64
internal inline fun IMap.getPTxn() : BigInt64? = this[COL_PTXN]
internal inline fun IMap.setPTxn(ptxn: BigInt64?) { this[COL_PTXN] = ptxn }

internal inline fun IMap.hasPUid() : Boolean = getAny(COL_PUID) is Int
internal inline fun IMap.getPUid() : Int? = this[COL_PUID]
internal inline fun IMap.setPUid(puid:Int?) { this[COL_PUID] = puid }

internal inline fun IMap.hasVersion() : Boolean = getAny(COL_VERSION) is BigInt64
internal inline fun IMap.getVersion() : BigInt64? = this[COL_VERSION]
internal inline fun IMap.setVersion(version:BigInt64?) { this[COL_VERSION] = version }

internal inline fun IMap.hasCreatedAt() : Boolean = getAny(COL_CREATED_AT) is BigInt64
internal inline fun IMap.getCreatedAt() : BigInt64? = this[COL_CREATED_AT]
internal inline fun IMap.setCreatedAt(createdAt : BigInt64?) { this[COL_CREATED_AT] = createdAt }

internal inline fun IMap.hasUpdatedAt() : Boolean = getAny(COL_UPDATE_AT) is BigInt64
internal inline fun IMap.getUpdatedAt() : BigInt64? = this[COL_UPDATE_AT]
internal inline fun IMap.setUpdatedAt(updatedAt : BigInt64?) { this[COL_UPDATE_AT] = updatedAt }

internal inline fun IMap.hasAuthorTs() : Boolean = getAny(COL_AUTHOR_TS) is BigInt64
internal inline fun IMap.getAuthorTs() : BigInt64? = this[COL_AUTHOR_TS]
internal inline fun IMap.setAuthorTs(authorTs : BigInt64?) { this[COL_AUTHOR_TS] = authorTs }

internal inline fun IMap.hasAction() : Boolean = getAny(COL_ACTION) is Short
internal inline fun IMap.getAction() : Short? = this[COL_ACTION]
internal inline fun IMap.setAction(action : Short?) { this[COL_ACTION] = action }

internal inline fun IMap.hasGeoType() : Boolean = getAny(COL_GEO_TYPE) is Short
internal inline fun IMap.getGeoType() : Short? = this[COL_GEO_TYPE]
internal inline fun IMap.setGeoType(geoType : Short?) { this[COL_GEO_TYPE] = geoType }

internal inline fun IMap.hasGeometry() : Boolean = getAny(COL_GEOMETRY) is ByteArray
internal inline fun IMap.getGeometry() : ByteArray? = this[COL_GEOMETRY]
internal inline fun IMap.setGeometry(geometry : ByteArray?) { this[COL_GEOMETRY] = geometry }

internal inline fun IMap.hasTags() : Boolean = getAny(COL_TAGS) is ByteArray
internal inline fun IMap.getTags() : ByteArray? = this[COL_TAGS]
internal inline fun IMap.setTags(tags : ByteArray?) { this[COL_TAGS] = tags }

internal inline fun IMap.hasFeature() : Boolean = getAny(COL_FEATURE) is ByteArray
internal inline fun IMap.getFeature() : ByteArray? = this[COL_FEATURE]
internal inline fun IMap.setFeature(feature : ByteArray?) { this[COL_FEATURE] = feature }

internal inline fun IMap.hasAuthor() : Boolean = getAny(COL_AUTHOR) is String
internal inline fun IMap.getAuthor() : String? = this[COL_AUTHOR]
internal inline fun IMap.setAuthor(author : String?) { this[COL_AUTHOR] = author }

internal inline fun IMap.hasAppId() : Boolean = getAny(COL_APP_ID) is String
internal inline fun IMap.getAppId() : String? = this[COL_APP_ID]
internal inline fun IMap.setAppId(appId : String?) { this[COL_APP_ID] = appId }

internal inline fun IMap.hasGeoGrid() : Boolean = getAny(COL_GEO_GRID) is String
internal inline fun IMap.getGeoGrid() : String? = this[COL_GEO_GRID]
internal inline fun IMap.setGeoGrid(geoGrid : String?) { this[COL_GEO_GRID] = geoGrid }

internal inline fun IMap.hasId() : Boolean = getAny(COL_ID) is String
internal inline fun IMap.getId() : String? = this[COL_ID]
internal inline fun IMap.setId(id : String?) { this[COL_ID] = id }

