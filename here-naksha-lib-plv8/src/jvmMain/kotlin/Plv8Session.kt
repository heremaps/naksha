package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.IJbThreadLocalSession
import com.here.naksha.lib.jbon.JvmSession
import java.sql.Connection

@Suppress("DuplicatedCode")
class Plv8Session : JvmSession() {
    private class Plv8SessionGetter : ThreadLocal<Plv8Session>(), IJbThreadLocalSession {
        override fun initialValue(): Plv8Session {
            return Plv8Session()
        }
    }

    companion object {
        @JvmStatic
        fun register() : Plv8Session {
            if (instance == null) {
                instance = Plv8SessionGetter()
            }
            return get()
        }

        @JvmStatic
        fun get() : Plv8Session {
            return instance!!.get() as Plv8Session
        }
    }

    /**
     * Sets the SQL connection to be used for the SQL interface.
     * @return this.
     */
    override fun setConnection(conn: Connection?): JvmSession {
        val existing = jvmSql
        if (existing != null) {
            if (existing.conn === conn) {
                return this
            }
            existing.conn.close()
            jvmSql = null
        }
        jvmSql = if (conn == null) null else Plv8Sql(conn)
        return this
    }

    override fun installModules(replacements: Map<String, String>?) {
        super.installModules(replacements)
        installModuleFromResource("naksha", "/here-naksha-lib-plv8.js", replacements=replacements)
        executeSqlFromResource("/plv8.sql", replacements)
    }
}