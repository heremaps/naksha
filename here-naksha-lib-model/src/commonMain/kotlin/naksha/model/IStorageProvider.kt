@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * A factory for storages, registered in `META-INF/services/naksha.model.IStorageProvider` and loaded using the `ServiceLoader`.
 * @since 3.0
 */
@JsExport
interface IStorageProvider {
    /**
     * The unique name of the provider, for example `v2`, `v3`, `null` or `random`.
     * @since 3.0
     */
    val name: String

    /**
     * A short description of the provider.
     * @since 3.0
     */
    val description: String

    /**
     * Creates a new builder that creates a storage configuration by asking questions.
     * @since 3.0
     */
    fun newConfigBuilder(): IStorageConfigBuilder

    /**
     * Returns the storage for the given configuration, the same instance for the same configuration. The method is thread safe.
     * @param config the configuration as JSON, see [newConfigBuilder].
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if the configuration is invalid.
     */
    fun getStorage(config: String): IStorage
}
