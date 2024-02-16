package com.here.naksha.lib.jbon

class JbMapIterator(val imap: IMap) : Iterator<Map.Entry<String,Any?>> {
    class MapEntry(override var key: String, override var value: Any?) : Map.Entry<String,Any?>
    private val it = Jb.map.iterator(imap)
    private val entry = MapEntry("", null)

    override fun hasNext(): Boolean {
        return it.hasNext()
    }

    override fun next(): Map.Entry<String, Any?> {
        check(it.next())
        entry.key = it.key()
        entry.value = it.value()
        return entry
    }
}