@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.Naksha
import naksha.model.Version
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Perform a read from the transaction log to query for [transaction features][naksha.model.objects.NakshaTx].
 * @since 3.0
 */
@JsExport
open class ReadTransactions : ReadFeatures() {
    init {
        mapId = Naksha.ADMIN_MAP
        collectionIds.add(Naksha.TRANSACTIONS_COL)
    }

    companion object ReadTransactions_C {
        /**
         * The [PlatformType] of [ReadTransactions].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ReadTransactions::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * Read the version.
     * @param version the version to read.
     * @return this.
     * @since 3.0
     */
    fun readVersion(version: Version): ReadTransactions {
        val versionString = version.toString()
        if (versionString !in featureIds) featureIds.add(versionString)
        return this
    }

    /**
     * Read the versions.
     * @param versions the versions to read.
     * @return this.
     * @since 3.0
     */
    fun readVersions(vararg versions: Version): ReadTransactions {
        for (version in versions) {
            val versionString = version.toString()
            if (versionString !in featureIds) featureIds.add(versionString)
        }
        return this
    }
}
