@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.service

import com.here.naksha.lib.auth.AccessRightsService
import com.here.naksha.lib.auth.action.*
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@JsExport
class NakshaService : AccessRightsService() {
    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "naksha"
    }

    fun createFeatures(): CreateFeatures = getOrCreate(CreateFeatures.NAME, CreateFeatures::class)
    fun readFeatures(): ReadFeatures = getOrCreate(ReadFeatures.NAME, ReadFeatures::class)
    fun updateFeatures(): UpdateFeatures = getOrCreate(UpdateFeatures.NAME, UpdateFeatures::class)
    fun deleteFeatures(): DeleteFeatures = getOrCreate(DeleteFeatures.NAME, DeleteFeatures::class)

    fun createCollections(): CreateCollections =
        getOrCreate(CreateCollections.NAME, CreateCollections::class)

    fun readCollections(): ReadCollections =
        getOrCreate(ReadCollections.NAME, ReadCollections::class)

    fun updateCollections(): UpdateCollections =
        getOrCreate(UpdateCollections.NAME, UpdateCollections::class)

    fun deleteCollections(): DeleteCollections =
        getOrCreate(DeleteCollections.NAME, DeleteCollections::class)

    fun useEventHandlers(): UseEventHandlers =
        getOrCreate(UseEventHandlers.NAME, UseEventHandlers::class)

    fun manageEventHandlers(): ManageEventHandlers =
        getOrCreate(ManageEventHandlers.NAME, ManageEventHandlers::class)

    fun useSpaces(): UseSpaces = getOrCreate(UseSpaces.NAME, UseSpaces::class)
    fun manageSpaces(): ManageSpaces = getOrCreate(ManageSpaces.NAME, ManageSpaces::class)

    fun useStorages(): UseStorages = getOrCreate(UseStorages.NAME, UseStorages::class)
    fun manageStorages(): ManageStorages = getOrCreate(ManageStorages.NAME, ManageStorages::class)
}