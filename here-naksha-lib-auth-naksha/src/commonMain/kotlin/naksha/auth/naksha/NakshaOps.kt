@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOps
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * All operations normally supported by Naksha.
 *
 * Technically, before executing a set of operations, each application, service, or library will create a [NakshaOps] map. Before executing an operation, the [UserRightsMatrix][naksha.auth.UserRightsMatrix] should be tested against the [NakshaOps] map. For example, a user wants to create a new space, the following is needed to test, if the user is allowed to do this:
 * ```kotlin
 * val urm = NakshaContext.currentContext().urm
 * val ops = NakshaOps()
 * // Send event into space.
 * ops.useSpaces += SpaceParams(space)
 * // The event modifies a space
 * ops.manageSpaces += SpaceParams(space)
 * // The event adds a handler to that space
 * ops.useEventHandlers +=
 *   UseEventHandlersParams(eventHandler, space.id)
 * if (!urm.matches(ops)) {
 *   // Access Denied!
 * }
 * ```
 *
 * @since 3.0
 */
@JsExport
class NakshaOps : ServiceOps() {
    companion object NakshaOps_C {
        /**
         * The [PlatformType] of [NakshaOps].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<NakshaOps> = forKClass(NakshaOps::class).withPackageName(PACKAGE_NAME)

        private val STORAGE_PARAMS_LIST_MEMBER = NotNullProperty<NakshaOps, StorageParamsList>(StorageParamsList.TYPE) {
                _, _ -> StorageParamsList()
        }
        private val SPACE_PARAMS_LIST_MEMBER = NotNullProperty<NakshaOps, SpaceParamsList>(SpaceParamsList.TYPE) {
           _,_ -> SpaceParamsList()
        }
        private val USE_EVENT_HANDLERS_PARAMS_LIST_MEMBER = NotNullProperty<NakshaOps, UseEventHandlersParamsList>(UseEventHandlersParamsList.TYPE) {
            _,_ -> UseEventHandlersParamsList()
        }
        private val MANAGE_EVENT_HANDLERS_PARAMS_LIST_MEMBER = NotNullProperty<NakshaOps, ManageEventHandlersParamsList>(ManageEventHandlersParamsList.TYPE) {
            _,_ -> ManageEventHandlersParamsList()
        }
        private val MAP_PARAMS_LIST_MEMBER = NotNullProperty<NakshaOps, MapParamsList>(MapParamsList.TYPE) {
            _,_ -> MapParamsList()
        }
        private val COLLECTION_PARAMS_LIST_MEMBER = NotNullProperty<NakshaOps, CollectionParamsList>(CollectionParamsList.TYPE) {
            _,_ -> CollectionParamsList()
        }
        private val FEATURE_PARAMS_LIST_MEMBER = NotNullProperty<NakshaOps, FeatureParamsList>(FeatureParamsList.TYPE) {
            _,_ -> FeatureParamsList()
        }
    }

    /**
     * When a user wants to use a storage.
     * @since 3.0
     * @see StorageParams
     */
    var useStorages: StorageParamsList by STORAGE_PARAMS_LIST_MEMBER

    /**
     * When a user wants to create, update or delete a storage.
     * @since 3.0
     * @see StorageParams
     */
    var manageStorages: StorageParamsList by STORAGE_PARAMS_LIST_MEMBER

    /**
     * When a user wants to send some event into the pipeline of a space, the user sending the event must have the `useSpaces` right for the corresponding space.
     * @since 3.0
     * @see SpaceParams
     */
    var useSpaces: SpaceParamsList by SPACE_PARAMS_LIST_MEMBER

    /**
     * When a user modifies a spaces.
     *
     * This right includes the right to modify the event-handler list of a space. However, adding a handler into a space requires, next to the [manageSpaces] right, additionally the [useEventHandlers] right _(this does not apply when removing a space)_.
     * @since 3.0
     * @see SpaceParams
     * @see useEventHandlers
     */
    var manageSpaces: SpaceParamsList by SPACE_PARAMS_LIST_MEMBER

    /**
     * When a user wants to add an event-handler into a space.
     * @since 3.0
     * @see UseEventHandlersParams
     */
    var useEventHandlers: UseEventHandlersParamsList by USE_EVENT_HANDLERS_PARAMS_LIST_MEMBER

    /**
     * Modify the event-handler itself.
     * @since 3.0
     * @see ManageEventHandlersParams
     */
    var manageEventHandlers: ManageEventHandlersParamsList by MANAGE_EVENT_HANDLERS_PARAMS_LIST_MEMBER

    /**
     * When a user wants to create a map.
     * @since 3.0
     * @see MapParams
     */
    var createMaps: MapParamsList by MAP_PARAMS_LIST_MEMBER

    /**
     * When a user wants to read a map.
     * @since 3.0
     * @see MapParams
     */
    var readMaps: MapParamsList by MAP_PARAMS_LIST_MEMBER

    /**
     * When a user wants to update a map.
     * @since 3.0
     * @see MapParams
     */
    var updateMaps: MapParamsList by MAP_PARAMS_LIST_MEMBER

    /**
     * When a user wants to delete a map.
     * @since 3.0
     * @see MapParams
     */
    var deleteMaps: MapParamsList by MAP_PARAMS_LIST_MEMBER

    /**
     * When a user wants to create a collection.
     * @since 3.0
     * @see CollectionParams
     */
    var createCollections: CollectionParamsList by COLLECTION_PARAMS_LIST_MEMBER

    /**
     * When a user wants to read a collection.
     * @since 3.0
     * @see CollectionParams
     */
    var readCollections: CollectionParamsList by COLLECTION_PARAMS_LIST_MEMBER

    /**
     * When a user wants to update a collection.
     * @since 3.0
     * @see CollectionParams
     */
    var updateCollections: CollectionParamsList by COLLECTION_PARAMS_LIST_MEMBER

    /**
     * When a user wants to delete a collection.
     * @since 3.0
     * @see CollectionParams
     */
    var deleteCollections: CollectionParamsList by COLLECTION_PARAMS_LIST_MEMBER

    /**
     * When a user wants to create a feature.
     * @since 3.0
     * @see FeatureParams
     */
    var createFeatures: FeatureParamsList by FEATURE_PARAMS_LIST_MEMBER

    /**
     * When a user wants to read a feature.
     * @since 3.0
     * @see FeatureParams
     */
    var readFeatures: FeatureParamsList by FEATURE_PARAMS_LIST_MEMBER

    /**
     * When a user wants to update a feature.
     * @since 3.0
     * @see FeatureParams
     */
    var updateFeatures: FeatureParamsList by FEATURE_PARAMS_LIST_MEMBER

    /**
     * When a user wants to delete a feature.
     * @since 3.0
     * @see FeatureParams
     */
    var deleteFeatures: FeatureParamsList by FEATURE_PARAMS_LIST_MEMBER
}