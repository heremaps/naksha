package naksha.jbon

import naksha.base.illegalArg
import kotlin.js.JsExport

/** Privately owned member names and shared slot lookup. Neither caller arrays nor mutable values are retained. Member-book string references address the parallel names section. @since 3.0 */
@JsExport
class MemberBookLayout(names: Array<String>) {
  private val names = names.copyOf()
  private val indices = HashMap<String, Int>()
  val size: Int get() = names.size

  init {
    for (i in this.names.indices) {
      val name = this.names[i]
      if (indices.put(name, i) != null) throw illegalArg("Duplicate member-book name '$name'")
    }
  }

  /** Resolve a fixed slot without allocating per-book lookup tables. @since 3.0 */
  fun indexOfName(name: String): Int = indices[name] ?: -1

  /** Read a name without exposing the privately owned array. @since 3.0 */
  fun getNameAt(index: Int): String? = names.getOrNull(index)
}
