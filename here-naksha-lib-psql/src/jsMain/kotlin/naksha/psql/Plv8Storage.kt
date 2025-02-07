@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.fn.Fx2
import naksha.jbon.JbDictionary
import naksha.model.Flags
import naksha.model.SessionOptions
import kotlin.reflect.KClass

/**
 * The PLV8 implementation of a storage, will be added to `plv8.storage`.
 */
@JsExport
class Plv8Storage : PgStorage() {
    override fun initAdminMap(config: PgConfig, create: Boolean?, upgrade: Boolean?): PgAdminMap {
        TODO("Not yet implemented")
    }

    override fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PgConnection {
        TODO("Not yet implemented")
    }

    override fun adminConnection(): PgConnection {
        TODO("Not yet implemented")
    }

    override val configKlass: KClass<PgConfig>
        get() = TODO("Not yet implemented")

    override fun afterInit() {
        TODO("Not yet implemented")
    }

    override fun shutdownStorage(dropCache: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getEncodingFlags(feature: Any?, context: Any?): Flags {
        TODO("Not yet implemented")
    }

    override fun getDictionary(id: String): JbDictionary? {
        TODO("Not yet implemented")
    }
}
