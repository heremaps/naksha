package naksha.model

import naksha.base.Id
import naksha.model.objects.NakshaDatabase
import kotlin.js.JsExport

/**
 * A simple fake implementation of [NakshaDatabase] for storages that do not actually need the database level; they can simply use this implementation.
 * @since 3.0
 * @see NakshaDatabase
 */
@JsExport
open class NakshaDatabaseImpl(
    override val storage: IStorage,
    override val id: Id = storage.id,
    override val isReplica: Boolean = false,
    override val isMaster: Boolean = true
) : NakshaDatabase