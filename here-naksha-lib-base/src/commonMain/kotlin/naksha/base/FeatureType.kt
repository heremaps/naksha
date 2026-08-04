@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.NakshaConst.IdConst_C.BOOK_TYPE
import naksha.base.NakshaConst.IdConst_C.CATALOG_TYPE
import naksha.base.NakshaConst.IdConst_C.COLLECTION_TYPE
import naksha.base.NakshaConst.IdConst_C.DATABASE_TYPE
import naksha.base.NakshaConst.IdConst_C.FEATURE_TYPE
import naksha.base.NakshaConst.IdConst_C.TRANSACTION_TYPE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * All possible Naksha intrinsic feature-types with their validation rules.
 *
 * Custom feature types can be introduced by extending this class, for example:
 * @since 3.0
 */
@JsExport
open class FeatureType : BaseEnum() {

    /**
     * Tests if the given **id** is a valid identifier of this kind.
     *
     * - `DATABASE` - `[a-z][a-z0-9_:-]{Naksha.MAX_ID_LENGTH}`
     * - `CATALOG` - `[a-z][a-z0-9_:-]{Naksha.MAX_ID_LENGTH}`
     * - `COLLECTION` - `[a-z][a-z0-9_:-]{Naksha.MAX_ID_LENGTH}`
     * - `BOOK` - `[a-z][a-z0-9_:-]{Naksha.MAX_ID_LENGTH}`
     * - `TRANSACTION` - `[1-9][0-9]{15}`
     * - `FEATURE` - no limit
     *
     * @param id the identifier to be tested.
     * @param internal if the internal variant of the identifier is to be tested.
     * @return the given identifier, if it is valid; otherwise throws an exception.
     * @throws NakshaException with [ILLEGAL_ID][NakshaError.NakshaErrorCompanion.ILLEGAL_ID], when `throwOnError` is _true_ and the identifier is not valid for the selected purpose (`idType`).
     * @since 3.0
     * @see [isValidId]
     */
    @JvmOverloads
    fun verify(id: String?, internal: Boolean = false): String {
        // n=3 because:
        // 3 = caller of this function
        // 2 = caller of isValidId (this function)
        // 1 = caller of fal
        // 0 = current function, would be `fal` itself
        verifier.isValidId(id, internal, throwOnError = true, 3)
        return id!!
    }


    /**
     * Tests if the given **id** is a valid identifier of this kind.
     *
     * - `DATABASE` - `[a-z][a-z0-9_:-]{Naksha.MAX_ID_LENGTH}`
     * - `CATALOG` - `[a-z][a-z0-9_:-]{Naksha.MAX_ID_LENGTH}`
     * - `COLLECTION` - `[a-z][a-z0-9_:-]{Naksha.MAX_ID_LENGTH}`
     * - `MEMBER` - `[a-z][a-z0-9_]{Naksha.MAX_ID_LENGTH}`
     * - `BOOK` - `[a-z][a-z0-9_:-]{Naksha.MAX_ID_LENGTH}`
     * - `TRANSACTION` - `[1-9][0-9]{15}`
     * - `FEATURE` - no limit
     *
     * **Beware**: Identifiers must not contain upper-case letters, because many storages does not make a difference between upper- and lower-cased letters.
     * @param id the identifier to test.
     * @param internal if the internal variant of the identifier is to be tested.
     * @param throwOnError if an exception should be thrown, when the verification failed.
     * @return _true_ if the identifier is valid; _false_ otherwise.
     * @throws NakshaException with [ILLEGAL_ID][NakshaError.NakshaErrorCompanion.ILLEGAL_ID], when `throwOnError` is _true_ and the identifier is not valid for the selected purpose (`idType`).
     * @since 3.0
     * @see [verify]
     */
    @JvmOverloads
    fun isValidId(id: String?, internal: Boolean = false, throwOnError: Boolean = false): Boolean {
        // n=3 because:
        // 3 = caller of this function
        // 2 = caller of isValidId (this function)
        // 1 = caller of fal
        // 0 = current function, would be `fal` itself
        return verifier.isValidId(id, internal, throwOnError = true, 3)
    }

    private var verifier: IdVerifier = IdVerifier.ANY

    override fun namespace(): KClass<out BaseEnum> = FeatureType::class
    override fun initClass() {}

    companion object FeatureType_C {
        /**
         * The identifiers for `NakshaDatabase`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val DATABASE = def(FeatureType::class, DATABASE_TYPE) { self ->
            self.verifier = IdVerifier.DATABASE_AND_STORAGE
        }

        /**
         * The identifiers for `NakshaCatalog`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val CATALOG = def(FeatureType::class, CATALOG_TYPE) { self ->
            self.verifier = IdVerifier.CATALOG_AND_COLLETION
        }

        /**
         * The identifiers for `NakshaCollection`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val COLLECTION = def(FeatureType::class, COLLECTION_TYPE) { self ->
            self.verifier = IdVerifier.CATALOG_AND_COLLETION
        }

        /**
         * The identifiers for `NakshaFeature`, actually without limits.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val FEATURE = def(FeatureType::class, FEATURE_TYPE) { self ->
            self.verifier = IdVerifier.ANY
        }

        /**
         * The identifiers for `Book` _(not yet implemented)_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val BOOK = def(FeatureType::class, BOOK_TYPE) { self ->
            self.verifier = IdVerifier.ANY
        }

        /**
         * The identifiers for transactions.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TRANSACTION = def(FeatureType::class, TRANSACTION_TYPE) { self ->
            self.verifier = IdVerifier.TRANSACTION
        }

        /**
         * Return the feature-type based upon the textual representation.
         * @param text the textual feature-type _(to be found in the `type` property)_.
         * @return the [FeatureType] matching the given text or `null`, if no type can be detected. In doubt then use [FEATURE].
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun fromString(text: String?): FeatureType? = getDefined(text, FeatureType::class)

        /**
         * Tries to detect feature-type based upon the given object.
         * @param obj the object.
         * @return the [FeatureType] of the given object or `null`, if no [FeatureType] can be detected. In doubt then use [FEATURE].
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun detect(obj: Any?): FeatureType? {
            // TODO: We should add support for more types like POJO.
            return if (obj is PTypedMap<*, *>) fromString(obj.getRaw("type") as? String?) else null
        }
    }
}
