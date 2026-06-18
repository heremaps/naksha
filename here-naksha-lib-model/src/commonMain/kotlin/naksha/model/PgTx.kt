@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictReader
import naksha.model.objects.*
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A wrapper for an ongoing Naksha transaction, used by [write session's][IWriteSession].
 *
 * This is a rather low level class, that normally applications should not care about, and which is not exposed by the session. It implements the _standard_ way of [Tuple] encoding and decoding. It helps storage implementors, and test implementations to generate [Tuple] without a storage.
 *
 * This class works without any real [storage][IStorage], however, storages can extend this class, and override certain behaviors with own better implementations, adjusted to the storage.
 *
 * @since 3.0
 */
@JsExport
open class PgTx private constructor(
    /**
     * The storage instance for which this transaction is done. Does not have to be supplied.
     * @since 3.0
     * @see [IStorage]
     */
    val storage: IStorage? = null,

    /**
     * The storage-number of the storage for which this transaction is done.
     * @since 3.0
     * @see [NakshaStorage.number]
     */
    val storageNumber: Int64,

    /**
     * The unique version of the transaction. This value **should be** unique to this transaction.
     * @since 3.0
     * @see [Version.auto]
     * @see [Version.manual]
     * @see [Version.now]
     */
    val version: Version,

    /**
     * The application-id of the application performing the modifications.
     * @since 3.0
     */
    val appId: String,

    /**
     * The author _(user)_ that performs the modifications; if any.
     *
     * If `null`, then the change is done by an application and the author and authorTs fields in [Tuple.membersBook] are not modified _(they stay what they are right now)_.
     * @since 3.0
     */
    val author: String?,

    /**
     * The dictionary reader to be used to encode and decode features.
     * @since 3.0
     */
    val dictReader: IDictReader?,

    /**
     * The session to which this transaction is attached.
     * @since 3.0
     */
    val session: IWriteSession,
) {

    @JsName("storageTxWithStorageNumber")
    constructor(
        storageNumber: Int64,
        version: Version,
        appId: String,
        author: String?,
        dictReader: IDictReader?,
        session: IWriteSession,
    ): this(null, storageNumber, version, appId, author, dictReader, session)

    @JsName("storageTxWithStorage")
    constructor(
        storage: IStorage,
        version: Version,
        appId: String,
        author: String?,
        dictReader: IDictReader?,
        session: IWriteSession,
    ): this(storage, storage.number, version, appId, author, dictReader, session)

    /**
     * The statistical transaction information, updated while this class is being used, should eventually be writted into the transaction-log of the storage.
     * @since 3.0
     */
    open val nakshaTx: NakshaTx = NakshaTx().setVersion(version)

    /**
     * The `updated_at` value being used for all [Tuple] created, basically just reads `transaction.time`.
     * @since 3.0
     */
    open val updatedAt: Int64
        get() = nakshaTx.time
}
