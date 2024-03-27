package com.here.naksha.lib.plv8

class ArenaSpace(val arenaId: String?) {

    /**
     * Returns either empty String "", or tablespace query part "TABLESPACE tablespace_name"
     */
    fun mainTablespaceQueryPart(): String {
        return if (arenaId == null) "" else " TABLESPACE ${MAIN_TABLESPACE_TEMPLATE.replace("{id}", arenaId)}"
    }

    fun headPartitionTablespaceQueryPart(partition: Int): String {
        return if (arenaId == null) "" else " TABLESPACE ${HEAD_TABLESPACE_TEMPLATE.replace("{id}", arenaId)}$partition"
    }

    fun hstPartitionTablespaceQueryPart(partition: Int): String {
        return if (arenaId == null) "" else " TABLESPACE ${HST_TABLESPACE_TEMPLATE.replace("{id}", arenaId)}$partition"
    }
}