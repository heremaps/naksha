package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class AccessRightsMatrix (vararg args: Any?): BaseObject(args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<AccessRightsMatrix>() {
            override fun isInstance(o: Any?): Boolean = o is AccessRightsMatrix

            override fun newInstance(vararg args: Any?): AccessRightsMatrix = AccessRightsMatrix()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun getAttributesForAction(actionName: String): List<AccessAttributeMap>? =
        get(actionName) as List<AccessAttributeMap>
}

class AccessAttributeMap: Map<String, Any> by HashMap()
