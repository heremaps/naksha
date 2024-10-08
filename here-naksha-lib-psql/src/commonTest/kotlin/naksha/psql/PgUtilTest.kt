package naksha.psql

import naksha.model.Flags
import naksha.model.XyzNs
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import naksha.psql.util.ProxyBuilder.make
import kotlin.test.Test
import kotlin.test.assertNotNull

class PgUtilTest {

    @Test
    fun shouldDecodeEncodedFeature() {
        // Given
        val beforeEncoding = NakshaFeature().apply {
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
        val encoded = PgUtil.encodeFeature(beforeEncoding, noSpecialEncoding)

        // And:
        val decoded = PgUtil.decodeFeature(encoded, noSpecialEncoding)

        // Then: features are equal but decoded one is missing Xyz
        // note: Xyz  should be populated after decoding (it's not stored in `feature` column, it's scattered in other columns)
        assertNotNull(decoded)
        assertThatFeature(beforeEncoding)
            .isIdenticalTo(decoded, ignoreProps = true)
            .hasPropertiesThat { decodedProperties ->
                decodedProperties
                    .hasFeatureType(beforeEncoding.properties.featureType)
                    .hasXyzThat { it.isEmpty() }
            }
    }
}