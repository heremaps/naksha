package com.here.naksha.lib.auth

import com.here.naksha.lib.base.*

fun <T : BaseType> BaseList<Any?>.toObjectList(type: BaseKlass<T>): List<T> {
    return iterator()
        .asSequence()
        .mapNotNull { it.valueAsObject(type) }
        .toList()
}
inline fun <reified T : BaseType> BaseList<Any?>.toObjectArray(type: BaseKlass<T>): Array<T> {
    return iterator()
        .asSequence()
        .mapNotNull { it.valueAsObject(type) }
        .toList()
        .toTypedArray()
}

fun List<*>.toBaseArray(): BaseArray<*> =
    BaseArray(*toTypedArray())

fun <T : BaseType> RawPair<Int, Any?>.valueAsObject(type: BaseKlass<T>): T? =
    value?.let { Base.assign(it, type) }
