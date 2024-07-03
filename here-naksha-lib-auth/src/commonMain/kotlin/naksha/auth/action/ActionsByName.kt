package naksha.auth.action

import kotlin.reflect.KClass

val ACTIONS_BY_NAME: Map<String, KClass<out AccessRightsAction<*, *>>> = mapOf(
    // collections
    ReadCollections.NAME to ReadCollections::class,
    CreateCollections.NAME to CreateCollections::class,
    UpdateCollections.NAME to UpdateCollections::class,
    DeleteCollections.NAME to DeleteCollections::class,

    // event handlers
    UseEventHandlers.NAME to UseEventHandlers::class,
    ManageEventHandlers.NAME to ManageEventHandlers::class,

    // features
    ReadFeatures.NAME to ReadFeatures::class,
    CreateFeatures.NAME to CreateFeatures::class,
    UpdateFeatures.NAME to UpdateFeatures::class,
    DeleteFeatures.NAME to DeleteFeatures::class,

    // spaces
    UseSpaces.NAME to UseSpaces::class,
    ManageSpaces.NAME to ManageSpaces::class,

    // storages
    UseStorages.NAME to UseStorages::class,
    ManageStorages.NAME to ManageStorages::class
)
