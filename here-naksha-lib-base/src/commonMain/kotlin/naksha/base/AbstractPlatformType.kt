package naksha.base

/**
 * Base implementation for [PlatformType] to avoid duplicated efforts in implementations.
 * @since 3.0
 */
abstract class AbstractPlatformType<T: Any> internal constructor(): PlatformType<T> {

    private var _isFeature: Boolean? = null
    override val isFeature: Boolean
        get() = _isFeature ?: superType?.isFeature ?: false
    override fun withIsFeature(value: Boolean?): PlatformType<T> {
        if (value == true) {
            _isFeature = true
            _isFeatureCollection = false
        } else {
            _isFeature = value
        }
        return this
    }

    private var _isFeatureCollection: Boolean? = null
    override val isFeatureCollection: Boolean
        get() = _isFeatureCollection ?: superType?.isFeatureCollection ?: false
    override fun withIsFeatureCollection(value: Boolean?): PlatformType<T> {
        if (value == true) {
            _isFeature = false
            _isFeatureCollection = true
        } else {
            _isFeatureCollection = value
        }
        return this
    }

    private var _isMomType: Boolean? = null
    override val isMomType: Boolean
        get() = _isMomType ?: superType?.isMomType ?: false
    override fun withIsMomType(value: Boolean?): PlatformType<T> {
        if (value == true && !isFeature && !isFeatureCollection) _isFeature = true
        _isMomType = value
        return this
    }

    private var _isDataHubType: Boolean? = null
    @Suppress("OVERRIDE_DEPRECATION")
    override val isDataHubType: Boolean
        get() = _isDataHubType ?: superType?.isDataHubType ?: false
    @Suppress("OVERRIDE_DEPRECATION")
    override fun withIsDataHubType(value: Boolean?): PlatformType<T> {
        if (value == true && !isFeature && !isFeatureCollection) _isFeature = true
        _isDataHubType = value
        return this
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = Platform.identityHashCode(this)
    override fun toString(): String = name
}