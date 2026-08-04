@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.TupleNumber
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An immutable array of tuple-number, can be encoded as binary.
 * @since 3.0
 */
@JsExport
interface ITupleNumberArray {
    /**
     * Returns the size of the array.
     * @since 3.0
     */
    val size: Int

    /**
     * Returns the database-number at the given index.
     * @param i the index to read.
     * @return the database-number.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if the given index is out of bounds.
     */
    fun getDatabaseNumber(i: Int): Long

    /**
     * Returns the catalog-number at the given index.
     * @param i the index to read.
     * @return the catalog-number.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if the given index is out of bounds.
     */
    fun getCatalogNumber(i : Int): Int

    /**
     * Returns the collection-number at the given index.
     * @param i the index to read.
     * @return the collection-number.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if the given index is out of bounds.
     */
    fun getCollectionNumber(i: Int): Int

    /**
     * Returns the feature-number at the given index.
     * @param i the index to read.
     * @return the feature-number.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if the given index is out of bounds.
     */
    fun getFeatureNumber(i: Int): Long

    /**
     * Returns the version at the given index.
     * @param i the index to read.
     * @return the version.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if the given index is out of bounds.
     */
    fun getVersion(i: Int): Long

    /**
     * Returns the [TupleNumber][naksha.base.TupleNumber] at the given index.
     * @param i the index to read.
     * @return the [TupleNumber][naksha.base.TupleNumber].
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if the given index is out of bounds.
     */
    fun getTupleNumber(i: Int): TupleNumber
}