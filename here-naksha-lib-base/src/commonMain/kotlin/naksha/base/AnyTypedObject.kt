@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forInstance
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_remove
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_set
import naksha.base.bugs.KT_68775_infinite_loop_for_calling_super_getter
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The base variant of an object that serializes its own type, mainly in a discriminator named `type`. The keys of the objects are [String], and values can be anything.
 *
 * In the past, [HERE Technologies](https://here.com) introduced a custom type property named `featureType`, located in the `properties` of GeoJSON features, used by internal services. At that time, there was no formal GeoJSON standard _(before [RFC-7946](https://datatracker.ietf.org/doc/html/rfc7946))_. Later the [MOM specification](https://www.here.com/learn/blog/unimap-map-object-model) appeared, and the governance board decided to deprecate the `properties.featureType`, and to relocate the `type` information into the GeoJSON feature, renaming it into `momType`.
 *
 * This lead to the situation that, next to the standard discriminator property `type`, there are now at least three more different ways to store the type of JSON objects within [HERE Technologies](https://here.com), creating a total of four possibilities:
 *
 * - The standard discriminator, normally supported by all parsers and tools, to use the `type` property within an object to detect the type.
 * - Then there is a common alternative, where the type in stored the property `featureType`. This is especially very common within [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946) features, and used as well outside [HERE Technologies](https://here.com).
 * - Lately, [HERE Technologies](https://here.com) created the [Map Object Model](https://www.here.com/learn/blog/unimap-map-object-model), extending the [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946) data model. The [MOM](https://www.here.com/learn/blog/unimap-map-object-model) defines that the object type information **must** be stored in a property named `momType`. This was necessary, because the [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946) requires that the `type` property is set to `"Feature"` for all [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946) features.
 * - In the past, [HERE Technologies](https://here.com) used `properties.featureType` for [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946) features to store proprietary type identifiers, which is very unlucky, because this property is located in a child object.
 *
 * Naksha team decided to support, as good as possible, the old style `properties.featureType`, the new style `momType`, and the two external standard ways of storing type information, with `type` and/or `featureType` property. While Naksha supports the old and new [HERE Technologies](https://here.com) type locations, it works best with the standard way of storing the type in the `type` and/or `featureType` property, and comes with corresponding first class support.
 *
 * Generally, there is the method [Platform.box] that performs the type detection, it is as well invoked by [Platform.fromJson] for the root object, if the root is a [PlatformMap]. To support the [HERE Technologies](https://here.com) type locations, `lib-base` by default injects the [AnyTypedObjectDetector], which implements a special handling when `type` is `"Feature"`. In that case, it checks for `momType`, then for `properties.featureType`, and finally for `featureType` to detect the real type. For this it uses the [first JSON type][Platform.forFirstJsonType] that extends [AnyTypedObject], and dynamically bind it to the [PlatformMap].
 *
 * All features with `type` being `"Feature"`, that have no special type, are parsed into [AnyTypedIdObject]. Note that `lib-geo` overrides this behavior, and uses as default match the type `GeoFeature`, that extends [AnyTypedIdObject].
 *
 * @since 3.0
 * @see DataViewProxy
 * @see AnyList
 * @see AnyMap
 * @see AnyObject
 * @see AnyTypedIdObject
 * @see AnyTypedObjectDetector
 */
@Suppress("DEPRECATION")
@JsExport
open class AnyTypedObject : AnyObject() {
    init {
        @Suppress("LeakingThis")
        jsonType_init()
    }

    companion object AnyTypedObject_C {
        /**
         * The [PlatformType] of [AnyTypedObject].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyTypedObject::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }

    /**
     * If this is a feature, then `type` should be `"Feature"`, and the [json-type][PlatformType.jsonType] is stored in `featureType`.
     *
     * Must be _true_ when [isDataHubType] or [isMomType].
     * @since 3.0
     * @see type
     * @see isFeature
     * @see isMomType
     * @see isDataHubType
     */
    open fun isFeature(): Boolean = isDataHubType() || isMomType()

    /**
     * If this is a [MOM](https://www.here.com/learn/blog/unimap-map-object-model) type, the [json-type][PlatformType.jsonType] will be stored in the `momType` property instead of the `featureType`.
     *
     * @since 3.0
     * @see type
     * @see isFeature
     * @see isMomType
     * @see isDataHubType
     */
    open fun isMomType(): Boolean = false

    /**
     * If this is an old Data-Hub type, the [json-type][PlatformType.jsonType] must be managed additionally within `properties.featureType`, additionally to either `featureType` or `momType`.
     *
     * This is only for backward compatibility and will be removed in the future, do **not use**, unless necessary.
     * @since 3.0
     * @see type
     * @see isFeature
     * @see isMomType
     * @see isDataHubType
     */
    @Deprecated(
        message = "Only for downward compatibility, do not use for new types",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("")
    )
    open fun isDataHubType(): Boolean = false

    /**
     * The JSON type of the feature.
     *
     * @since 3.0
     * @see type_get
     * @see type_set
     * @see isFeature
     * @see isMomType
     * @see isDataHubType
     */
    @KT_68775_infinite_loop_for_calling_super_getter
    open val type: String?
        get() = type_get()

    /**
     * Sets or clears the JSON type.
     *
     * If `null` is given, and not [isFeature], remove `type` property and return. Otherwise, if `null` given and [isFeature]:
     * - Set `type` to `"Feature"` and remove `featureType`
     * - If [isMomType], remove `momType`.
     * - If [isDataHubType], remove `properties.featureType`
     *
     * If a JSON type is given, and not [isFeature], update `type` and return. Otherwise, if [isFeature]:
     * - Set `type` to `"Feature"`
     * - If [isMomType], set `momType`, else, if not [isDataHubType], set `featureType`
     * - If [isDataHubType], set `properties.featureType`
     *
     * @param type The type to set.
     * @return this.
     * @see type_set
     */
    open fun withType(type: String?): AnyTypedObject {
        type_set(type)
        return this
    }

    /**
     * The JSON type of this object.
     *
     * This is a special handling, it performs the following checks in order:
     * - If `type` is string and not `"Feature"`, returns the `type` value.
     * - If `type` is `"Feature"`,
     *     - and [isMomType], and `momType` is a string, returns the `momType` value.
     *     - and [isDataHubType], and `properties.featureType` is a string, returns the `properties.featureType` value.
     *     - and `featureType` is a string, returns the `featureType` value.
     *     - and [PlatformType.jsonType] is not `null`, return [PlatformType.jsonType] value.
     *     - returns `"Feature"`
     * - Eventually returns [PlatformType.jsonType]
     * @since 3.0
     * @see type
     * @see isFeature
     * @see isMomType
     * @see isDataHubType
     */
    @KT_68775_infinite_loop_for_calling_super_getter
    protected open fun type_get(): String? {
        val po = platformObject()
        var raw = map_get(po, "type")
        if (raw is String) {
            if (raw != FEATURE) return raw

            if (isMomType()) {
                raw = map_get(po, "momType")
                if (raw is String) return raw
            }

            if (isDataHubType()) {
                val properties = map_get(po, "properties")
                if (properties is PlatformMap) {
                    raw = map_get(properties, "featureType")
                    if (raw is String) return raw
                }
            }

            raw = map_get(po, "featureType")
            if (raw is String) return raw

            return forInstance(this).jsonType ?: FEATURE
        }
        // Type not encoded, use the platform-type value
        return forInstance(this).jsonType
    }

    /**
     * Sets or clears the JSON type.
     *
     * If `null` is given, and not [isFeature], remove `type` property and return. Otherwise, if `null` given and [isFeature]:
     * - Set `type` to `"Feature"` and remove `featureType`
     * - If [isMomType], remove `momType`.
     * - If [isDataHubType], remove `properties.featureType`
     *
     * If a JSON type is given, and not [isFeature], update `type` and return. Otherwise, if [isFeature]:
     * - Set `type` to `"Feature"`
     * - If [isMomType], set `momType`, else, if not [isDataHubType], set `featureType`
     * - If [isDataHubType], set `properties.featureType`
     *
     * @since 3.0
     * @see type
     * @see jsonType
     * @see isFeature
     * @see isMomType
     * @see isDataHubType
     */
    @KT_68775_infinite_loop_for_calling_super_getter
    protected open fun type_set(jsonType: String?) {
        val po = platformObject()
        // Clear type.
        if (jsonType == null) {
            if (!isFeature()) {
                map_remove(po, "type")
                return
            }

            map_set(po, "type", FEATURE)
            map_remove(po, "featureType")
            if (isMomType()) {
                map_remove(po, "momType")
            }
            if (isDataHubType()) {
                val properties = map_get(po, "properties")
                if (properties is PlatformMap) {
                    map_remove(properties, "featureType")
                }
            }
            return
        }

        // Update type.
        if (!isFeature()) {
            map_set(po, "type", jsonType)
            return
        }

        map_set(po, "type", FEATURE)
        if (jsonType != FEATURE) {
            if (isMomType()) {
                map_set(po, "momType", jsonType)
            } else if (!isDataHubType()) {
                map_set(po, "featureType", jsonType)
            }
            if (isDataHubType()) { // properties.featureType
                var properties = map_get(po, "properties")
                if (properties !is PlatformMap) {
                    properties = Platform.newMap()
                    map_set(po, "properties", properties)
                }
                map_set(properties, "featureType", jsonType)
            }
        }
    }

    /**
     * Automatically invoked by the constructor of [AnyTypedObject].
     *
     * Reads [PlatformType.jsonType] of `this` and invokes [type_set].
     * @since 3.0
     * @see type
     * @see isFeature
     * @see isMomType
     * @see isDataHubType
     */
    protected open fun jsonType_init() {
        type_set(forInstance(this).jsonType)
    }

}