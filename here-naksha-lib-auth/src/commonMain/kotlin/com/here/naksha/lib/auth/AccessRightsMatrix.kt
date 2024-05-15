package com.here.naksha.lib.auth

import com.here.naksha.lib.base.*
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class AccessRightsMatrix(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<AccessRightsMatrix>() {
            override fun isInstance(o: Any?): Boolean = o is AccessRightsMatrix

            override fun newInstance(vararg args: Any?): AccessRightsMatrix =
                AccessRightsMatrix()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun getAccessMatrixForService(serviceName: String): AccessServiceRights? =
        getOrNull(serviceName, AccessServiceRights.klass)
}

class AccessServiceRights(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<AccessServiceRights>() {
            override fun isInstance(o: Any?): Boolean = o is AccessServiceRights

            override fun newInstance(vararg args: Any?): AccessServiceRights =
                AccessServiceRights()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    fun getAttributesForAction(actionName: String): List<AccessAttributes>? =
        getOrNull(actionName, BaseList.klass)?.toObjectList(AccessAttributes.klass)
}


class AccessAttributes(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<AccessAttributes>() {
            override fun isInstance(o: Any?): Boolean = o is AccessAttributes

            override fun newInstance(vararg args: Any?): AccessAttributes =
                AccessAttributes()
        }
    }

    override fun klass(): BaseKlass<*> = klass
}
