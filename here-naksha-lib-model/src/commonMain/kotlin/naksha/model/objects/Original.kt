package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NullableProperty

/**
 * Subset of data stored under activity log.
 */
class Original : AnyObject() {
    companion object {
        private val SPACE = NullableProperty<Original, String>(String::class)
        private val CREATED_AT = NullableProperty<Original, Long>(Long::class)
        private val UPDATED_AT = NullableProperty<Original, Long>(Long::class)
        private val PUUID = NullableProperty<Original, String>(String::class)
        private val MUUID = NullableProperty<Original, String>(String::class)
    }
    /** The space ID the feature belongs to. */
    var space by SPACE

    /** The timestamp, when the feature was created. */
    var createdAt by CREATED_AT

    /** The timestamp, when the feature was last updated. */
    var updatedAt by UPDATED_AT

    /** The previous uuid of the feature. */
    var puuid by PUUID

    /** The merge muuid of the feature. */
    var muuid by MUUID
}