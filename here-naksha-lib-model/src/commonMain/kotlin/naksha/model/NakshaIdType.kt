@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.Naksha.NakshaCompanion.MAX_ID_LENGTH
import naksha.model.Naksha.NakshaCompanion.MAX_INTERNAL_ID_LENGTH
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads

/**
 * All possible identifier types in Naksha with their validation rules.
 * @property start the characters allowed as first character.
 * @property chars the characters allowed at the rest of the identifier.
 * @property rangeInfo informational string for error messages which characters are allowed.
 */
@JsExport
enum class NakshaIdType(
    internal val start: Map<Char, Boolean>,
    internal val chars: Map<Char, Boolean>,
    internal val maxLength: Int,
    internal val rangeInfo: String,
    internal val isInternal: Boolean = false,
) {
    /**
     * The identifiers for `NakshaDatabase` _(not yet implemented)_.
     * @since 3.0
     */
    DATABASE(_start(), _container(), MAX_ID_LENGTH, "[a-z][a-z0-9_-:]{${MAX_ID_LENGTH-1}}"),

    /**
     * The identifiers for internal `NakshaDatabase` _(not yet implemented)_.
     * @since 3.0
     */
    INTERNAL_DATABASE(_start(true), _container(true), MAX_INTERNAL_ID_LENGTH, "[a-z][a-z0-9_-:$~]{${MAX_INTERNAL_ID_LENGTH-1}}", true),

    /**
     * The identifiers for [NakshaCatalog][naksha.model.objects.NakshaCatalog].
     * @since 3.0
     */
    CATALOG(_start(), _container(), MAX_ID_LENGTH, "[a-z][a-z0-9_-:]{${MAX_ID_LENGTH}}"),

    /**
     * The identifiers for internal [NakshaCatalog][naksha.model.objects.NakshaCatalog].
     * @since 3.0
     */
    INTERNAL_CATALOG(_start(true), _container(true), MAX_INTERNAL_ID_LENGTH, "[a-z][a-z0-9_-:$~]{${MAX_INTERNAL_ID_LENGTH-1}}", true),

    /**
     * The identifiers for [NakshaCollection][naksha.model.objects.NakshaCollection].
     * @since 3.0
     */
    COLLECTION(_start(), _container(), MAX_ID_LENGTH, "[a-z][a-z0-9_-:]{${MAX_ID_LENGTH-1}}"),

    /**
     * The identifiers for internal [NakshaCollection][naksha.model.objects.NakshaCollection].
     * @since 3.0
     */
    INTERNAL_COLLECTION(_start(true), _container(true), MAX_INTERNAL_ID_LENGTH, "[a-z][a-z0-9_-:$~]{${MAX_INTERNAL_ID_LENGTH-1}}", true),

    /**
     * The identifiers for [Member][naksha.model.objects.Member].
     * @since 3.0
     */
    MEMBER(_start(), _member(), MAX_ID_LENGTH, "[a-z][a-z0-9_]{${MAX_ID_LENGTH-1}}"),

    /**
     * The identifiers for internal [Member][naksha.model.objects.Member].
     * @since 3.0
     */
    INTERNAL_MEMBER(_start(true), _member(true), MAX_ID_LENGTH, "[a-z_][a-z0-9_]{${MAX_ID_LENGTH-1}}", true),

    /**
     * The identifiers for [Index][naksha.model.objects.Index].
     * @since 3.0
     */
    INDEX(_start(), _member(), MAX_ID_LENGTH, "[a-z][a-z0-9_]{${MAX_ID_LENGTH-1}}"),

    /**
     * The identifiers for internal [Index][naksha.model.objects.Index].
     * @since 3.0
     */
    INTERNAL_INDEX(_start(true), _member(true), MAX_ID_LENGTH, "[a-z_][a-z0-9_]{${MAX_ID_LENGTH-1}}", true),

    /**
     * The identifiers for `Book` _(not yet implemented)_.
     * @since 3.0
     */
    BOOK(_start(), _member(), MAX_ID_LENGTH, "[a-z][a-z0-9_]{${MAX_ID_LENGTH-1}}"),

    /**
     * The identifiers for internal `Book` _(not yet implemented)_.
     * @since 3.0
     */
    INTERNAL_BOOK(_start(true), _member(true), MAX_INTERNAL_ID_LENGTH, "[a-z_][a-z0-9_]{${MAX_INTERNAL_ID_LENGTH-1}}"),

    /**
     * The identifiers for `Book` _(not yet implemented)_.
     * @since 3.0
     */
    TRANSACTION(_start_tx(), _tx(), 16, "[1-9][0-9]{15}"),

    /**
     * The identifiers for [NakshaFeature][naksha.model.objects.NakshaFeature], actually without limits.
     * @since 3.0
     */
    FEATURE(mapOf(), mapOf(), Int.MAX_VALUE, ".*");

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
     * @return the given identifier, if it is valid; otherwise throws an exception.
     * @throws NakshaException with [ILLEGAL_ID][naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ID], when `throwOnError` is _true_ and the identifier is not valid for the selected purpose (`idType`).
     * @since 3.0
     * @see [isValidId]
     */
    fun verify(id: String?): String {
        isValidId(id, true)
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
     * @param throwOnError if an exception should be thrown, when the verification failed.
     * @return _true_ if the identifier is valid; _false_ otherwise.
     * @throws NakshaException with [ILLEGAL_ID][naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ID], when `throwOnError` is _true_ and the identifier is not valid for the selected purpose (`idType`).
     * @since 3.0
     * @see [verify]
     */
    @JvmOverloads
    fun isValidId(id: String?, throwOnError: Boolean = false): Boolean {
        if (id.isNullOrEmpty()) {
            if (throwOnError) throw illegalId("The given identifier is null or empty")
            else return false
        }
        if (id == "naksha") {
            if (isInternal) return true
            if (throwOnError) throw illegalId("The identifier 'naksha' is forbidden")
            else return false
        }
        if (id.length > maxLength) {
            if (throwOnError) throw illegalId("The identifier '$id' is too long: ${id.length}, must be maximal $maxLength")
            else return false
        }
        var i = 0
        var c = id[i++]
        if (!start.containsKey(c)) {
            if (throwOnError) throw illegalId("The first character must be $rangeInfo, but was $c")
            else return false
        }
        while (i < id.length) {
            c = id[i++]
            if (!chars.containsKey(c)) {
                if (throwOnError) throw illegalId("Invalid character at index $i: '$c', expected $rangeInfo")
                else return false
            }
        }
        return true
    }
}

private fun _start_tx(): Map<Char, Boolean> {
    val map = mutableMapOf<Char, Boolean>()
    for (c in '1' .. '9') map[c] = true
    return map.toMap()
}
private fun _tx(): Map<Char, Boolean> {
    val map = mutableMapOf<Char, Boolean>()
    for (c in '0' .. '9') map[c] = true
    return map.toMap()
}
private fun _start(internal: Boolean = false): Map<Char, Boolean> {
    val map = mutableMapOf<Char, Boolean>()
    for (c in 'a' .. 'z') map[c] = true
    if (internal) map['_'] = true
    return map.toMap()
}
private fun _member(internal: Boolean = false): Map<Char, Boolean> {
    val map = mutableMapOf<Char, Boolean>()
    for (c in 'a' .. 'z') map[c] = true
    map['_'] = true
    return map.toMap()
}
private fun _container(internal: Boolean = false): Map<Char, Boolean> {
    val map = mutableMapOf<Char, Boolean>()
    for (c in 'a' .. 'z') map[c] = true
    map['_'] = true
    map['-'] = true
    map[':'] = true
    if (internal) {
        map['~'] = true
        map['$'] = true
    }
    return map.toMap()
}
