package com.here.naksha.app.service

import com.here.naksha.lib.core.INaksha
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig
import com.here.naksha.lib.core.models.naksha.EventTarget
import com.here.naksha.lib.handlers.DefaultStorageHandler
import naksha.base.AnyList
import naksha.base.Proxy
import naksha.model.objects.NakshaCollection

/** Test subclass that, like extensions with nested collections, defines its collections in its own property. */
class MultiCollectionStorageHandler(config: EventHandlerConfig, hub: INaksha, target: EventTarget<*>) :
    DefaultStorageHandler(config, hub, target) {

    override fun configuredCollections(): List<NakshaCollection> {
        val collections = Proxy.box(eventHandlerConfig.properties.getRaw("collections"), AnyList::class) ?: return listOf()
        return collections.mapNotNull { Proxy.box(it, NakshaCollection::class) }
    }
}
