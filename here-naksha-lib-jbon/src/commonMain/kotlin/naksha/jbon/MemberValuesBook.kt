package naksha.jbon

import naksha.base.PlatformUtil.PlatformUtilCompanion.hashCodeOf
import naksha.base.illegalArg
import kotlin.js.JsExport

/** Array-backed member book. Every instance owns its values array; only immutable names/lookups are shared. Copies are shallow, just as tuple book copies were before. @since 3.0 */
@JsExport
class MemberValuesBook(val layout: MemberBookLayout) : IBook {
  private val values = arrayOfNulls<Any?>(layout.size)
  override val bookType: BookType get() = BookType.MEMBER_BOOK
  override var id: String? = null
  override var databaseNumber: Long? = null
  override var featureNumber: Long? = null
  override val length: Int get() = values.size
  override fun get(index: Int): Any? = values.getOrNull(index)
  override fun get(name: String): Any? = get(layout.indexOfName(name))
  override fun indexOfString(string: String): Int = layout.indexOfName(string)
  override fun getStringAt(index: Int): String? = layout.getNameAt(index)
  override fun hasNames(): Boolean = true
  override fun indexOfName(name: String): Int = layout.indexOfName(name)
  override fun getNameAt(index: Int): String? = layout.getNameAt(index)
  override fun namesLength(): Int = layout.size

  /** Find matching value hashes without maintaining a second per-tuple map. Results refer to this book, including null slots and hash collisions. @since 3.0 */
  override fun getAllWithHash(hash: Int): List<DictEntry> {
    val result = ArrayList<DictEntry>()
    for (i in values.indices) {
      val value = values[i]
      if (hashCodeOf(value) == hash) result.add(DictEntry(this, i, value))
    }
    return result
  }

  /** Fill/update an existing slot; never compact, add names or expose the values array. @since 3.0 */
  fun setValue(index: Int, value: Any?) {
    if (index !in values.indices) throw illegalArg("Invalid member-book slot $index")
    values[index] = value
  }

  /** Update metadata in an existing named slot. @since 3.0 */
  fun setNamedValue(name: String, value: Any?) = setValue(layout.indexOfName(name), value)

  /** Copy values and metadata while sharing only the immutable layout. @since 3.0 */
  fun copy(): MemberValuesBook {
    val copy = MemberValuesBook(layout)
    values.copyInto(copy.values)
    copy.id = id
    copy.databaseNumber = databaseNumber
    copy.featureNumber = featureNumber
    return copy
  }

  companion object {
    /** Copy a named member book, preserving positional slots even when values are null. @since 3.0 */
    fun copyOf(book: IBook): MemberValuesBook {
      if (book is MemberValuesBook) return book.copy()
      if (!book.hasNames() || book.namesLength() != book.length) throw illegalArg("A tuple member book must have a name for every slot")
      val names = Array(book.length) { i -> book.getNameAt(i) ?: throw illegalArg("Missing member-book name at $i") }
      val copy = MemberValuesBook(MemberBookLayout(names))
      for (i in 0 until book.length) copy.values[i] = book[i]
      copy.id = book.id
      copy.databaseNumber = book.databaseNumber
      copy.featureNumber = book.featureNumber
      return copy
    }
  }
}
