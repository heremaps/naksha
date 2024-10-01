package naksha.psql

import kotlin.test.Test
import kotlin.test.assertEquals

class PgRelationTest {

    @Test
    fun shouldParseNameProperly() {
        // given
        val oid = 0
        val schemaOid = 0
        val tablespaceOid = 0
        val schemaName = "const"
        val kind = PgKind.PartitionedTable
        val pgStorageClass = PgStorageClass.Consistent
        val pgRelation: (String) -> PgRelation = { name -> PgRelation(
            oid, name, schemaOid, schemaName, kind, pgStorageClass, tablespaceOid
        ) }

        // expect
        assertEquals(1, pgRelation("topology\$p001").partitionNumber())
        assertEquals(0, pgRelation("topology\$p000").partitionNumber())
        assertEquals(256, pgRelation("topology\$del\$p256").partitionNumber())
        assertEquals(7, pgRelation("topology\$hst\$y2024\$p007").partitionNumber())
        assertEquals(2024, pgRelation("topology\$hst\$y2024\$p001").year())
        assertEquals(2024, pgRelation("topology\$hst\$y2024").year())
    }
}