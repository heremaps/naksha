package naksha.model

import naksha.base.Int64
import naksha.model.request.query.AnyOp
import naksha.model.request.query.MetaColumn
import naksha.model.request.query.MetaQuery
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertIs

class TupleNumberQueryTest {

    private val random = Random.Default

    @Ignore // TODO CASL-1140: analyze & either fix or remove this test
    @Test
    fun shouldStoreTnsAsByteArray() {
        // Given:
        val serializedTupleNumbers: Array<ByteArray> = arrayOf(
            randomTupleNumber().toByteArray(TupleNumberVariant.B64),
            randomTupleNumber().toByteArray(TupleNumberVariant.B64)
        )
        val metaQuery = MetaQuery(MetaColumn.nextVersion(), AnyOp.IS_ANY_OF, serializedTupleNumbers)

        // Then
        assertIs<Array<ByteArray>>(metaQuery.value)
    }

    private fun randomTupleNumber() =
        TupleNumber(
            storageNumber = Int64(random.nextInt(10)),
            mapNumber = random.nextInt(10),
            collectionNumber = random.nextInt(10),
            featureNumber = Int64(random.nextInt(10)),
            version = Version(Int64(random.nextInt(10)))
        )
}