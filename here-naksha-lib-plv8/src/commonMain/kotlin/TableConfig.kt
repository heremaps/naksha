package com.here.naksha.lib.plv8

class TableConfig(val temporary: Boolean) {

    /**
     * Returns either empty String "", or tablespace query part "TABLESPACE tablespace_name"
     */
    fun tablespaceQueryPart(): String {
        return if (temporary) " TABLESPACE $TEMPORARY_TABLESPACE" else ""
    }

    /**
     * Returns query part that marks collection as "UNLOGGED" if it's temporary
     */
    fun unlogged(): String {
        return if (temporary) "UNLOGGED" else ""
    }
}