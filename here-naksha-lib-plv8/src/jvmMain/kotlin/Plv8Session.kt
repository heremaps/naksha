package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.JvmSession

class Plv8Session : JvmSession() {
    override fun installModules() {
        super.installModules()
        installModuleFromResource("jbon", "/here-naksha-lib-plv8.js")
        executeSqlFromResource("/plv8.sql")
    }
}