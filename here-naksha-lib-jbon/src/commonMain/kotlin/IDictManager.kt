@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A thread safe dictionary manager.
 */
@JsExport
interface IDictManager {

    /**
     * Store the given dictionary into the manager.
     * @param dict The global dictionary to store.
     * @throws IllegalStateException If a dictionary with the same identifier exists already.
     */
    fun putDictionary(dict: JbDict)

    /**
     * Deleted the given dictionary from the manager.
     * @param dict The dictionary to delete.
     * @return _true_ if the dictionary was deleted; _false_ if this dictionary is not stored in the manager.
     */
    fun deleteDictionary(dict: JbDict) : Boolean

    /**
     * Retrieve the dictionary with the given identifier.
     * @param id The dictionary identifier to lookup.
     * @return The global dictionary with the given identifier; _null_ when no such dictionary exists.
     */
    fun getDictionary(id: String): JbDict?
}