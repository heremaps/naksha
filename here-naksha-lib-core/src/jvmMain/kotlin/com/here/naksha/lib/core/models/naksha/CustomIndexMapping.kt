@file:Suppress("OPT_IN_USAGE")
@file:JvmName("CustomIndexMappings")

package com.here.naksha.lib.core.models.naksha

import naksha.model.objects.*

import naksha.base.Proxy
import naksha.base.AnyObject
import naksha.base.ListProxy
import naksha.base.NullableEnum
import naksha.base.NullableProperty

/** A typed property to materialize and, except for booleans, index. Names are assigned by [com.here.naksha.lib.core.util.CustomIndexMappingCompiler]. */
class CustomIndexMapping() : AnyObject() {
    constructor(jsonPath: JsonPath, dataType: MemberType) : this() {
        this.jsonPath = jsonPath
        this.dataType = dataType
    }

    var jsonPath: JsonPath? by JSON_PATH
    var dataType: MemberType? by DATA_TYPE

    companion object {
        private val JSON_PATH = NullableProperty<CustomIndexMapping, JsonPath>(JsonPath::class)
        private val DATA_TYPE = NullableEnum<CustomIndexMapping, MemberType>(MemberType::class)
    }
}

class CustomIndexMappingList() : ListProxy<CustomIndexMapping>(CustomIndexMapping::class) {
    constructor(vararg mappings: CustomIndexMapping) : this() { addAll(mappings.toList()) }
}

/** Stored only in Space/Handler configuration. Removed from outgoing native definitions. */
var NakshaCollection.customIndexMapping: CustomIndexMappingList?
    get() = Proxy.box(getRaw("customIndexMapping"), CustomIndexMappingList::class)
    set(value) { this["customIndexMapping"] = value }
