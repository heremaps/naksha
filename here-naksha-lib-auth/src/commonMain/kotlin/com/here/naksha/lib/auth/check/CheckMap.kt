package com.here.naksha.lib.auth.check

import com.here.naksha.lib.auth.attribute.ResourceAttributes
import naksha.base.P_Map
import kotlin.reflect.KClass

class CheckMap : P_Map<String, Check>(String::class, UnknownOp::class) {
    companion object {
        /**
         * All registered operations.
         */
        private val all =
            HashMap<String, KClass<out Check>>() // TODO: Needs to be a concurrent hash map, add ConcurrentMap to lib-base!

        init {
            all[EqualsCheck.NAME] = EqualsCheck::class
            all[StartsWithCheck.NAME] = StartsWithCheck::class
            all[EndsWithCheck.NAME] = EndsWithCheck::class
        }
    }

    // Overriding this method will convert the values into individual instances, based upon the key.
    // The key is something like "equals", "startsWith", "endsWith", ...
    override fun toValue(key: String, value: Any?, alt: Check?): Check? {
        val opKlass = all[key] ?: return super.toValue(key, value, alt)
        return box(value, opKlass)
    }

    /**
     * Test all check-operations against the attribute value.
     * @param value The attribute value as read from [ResourceAttributes].
     * @return _true_ if all operations match; _false_ otherwise.
     */
    fun matches(value: Any?): Boolean {
        for (opName in keys) { // someOp, equals, ...
            val op = get(opName)
            if (op == null) {
                // TODO: Log an info and the operation is invalid (with the name)
                return false
            }
            if (op is UnknownOp) {
                // TODO: Log an info that we found an unknown op (with the name)
                return false
            }
            if (!op.matches(value)) return false
        }
        return true
    }
}
