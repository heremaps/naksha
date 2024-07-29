package naksha.model

import naksha.base.Int64
import kotlin.test.Test
import kotlin.test.assertEquals

class GuidTest {

    @Test
    fun shouldCreateGuidString() {
        // given
        val guid = Guid(
            storageId = "storageId",
            collectionId = "collectionId",
            featureId = "Feature1",
            luid = Luid(
                uid = 11,
                version = Version.of(2001, 11, 26, Int64(3))
            )
        )

        // expect
        assertEquals("storageId:collectionId:Feature1:2001:11:26:3:11", guid.toString())
    }

    @Test
    fun shouldTransformTxtToGuid() {
        // given
        val txn = Version.of(2001, 11, 26, Int64(3))

        // when
        val guid = txn.toGuid("naksha", "foo", "foo1")

        // then
        assertEquals("naksha:foo:foo1:2001:11:26:3:0", guid.toString())
        assertEquals(2001, txn.year())
        assertEquals(11, txn.month())
        assertEquals(26, txn.day())
        assertEquals(3, txn.seq().toInt())
    }
}