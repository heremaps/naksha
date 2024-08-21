package naksha.psql

import naksha.model.Flags
import naksha.model.XyzNs
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import naksha.psql.util.CommonProxyComparisons
import naksha.psql.util.CommonProxyComparisons.assertAnyObjectsEqual
import naksha.psql.util.ProxyBuilder
import naksha.psql.util.ProxyBuilder.make
import kotlin.test.Test
import kotlin.test.assertNotNull

class PgUtilTest {

    @Test
    fun shouldDecodeEncodedFeature(){
        // Given
        val feature = NakshaFeature().apply {
            id = "feature_1"
            properties = NakshaProperties().apply {
                featureType = "some_feature_type"
                xyz = make<XyzNs>(
                    "appId" to "someAppId",
                    "author" to "someAuthor"
                )
            }
        }

        // When:
        val noSpecialEncoding = Flags(0)
        val encoded = PgUtil.encodeFeature(feature, noSpecialEncoding)

        // And:
        val decoded = PgUtil.decodeFeature(encoded, noSpecialEncoding)

        // Then:
        assertNotNull(decoded)
        assertAnyObjectsEqual(feature, decoded)
    }
}