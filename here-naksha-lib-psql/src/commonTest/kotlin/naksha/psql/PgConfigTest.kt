package naksha.psql

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PgConfigTest {
    private val exampleUri = "jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password"
    private val config1 = PgConfig()
        .withMasterUri(exampleUri)
    private val config2 = PgConfig()
        .withMasterUri(exampleUri)

    @Test
    fun shouldBeEqualWhenIrrelevantRawParameterAdded() {
        // Given
        config1.setRaw("fake", "fake")

        // And
        config2.setRaw("fake", "fake2")

        // When & Then
        assertTrue(config1.configEquals(config2))
    }

    @Test
    fun shouldNotBeEqualWhenMastersDiffers() {
        // Given
        config1
            .withMaster(PgInstanceConfig.fromUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password"))
        // And
        config2
            .withMaster(PgInstanceConfig.fromUri("jdbc:postgresql://192.168.100.1:1234/db2?user=user&password=password"))

        // When & Then
        assertFalse(config1.configEquals(config2))
    }

    @Test
    fun shouldBeEqualWhenSameReplicasButInDifferentOrder() {
        // Given: first config with replicas
        config1.replicas.apply {
            addUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:123/db?user=user&password=password")
        }

        // And: second config with the same replicas in different order
        config2.replicas.apply {
            addUri("jdbc:postgresql://192.168.100.1:123/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password")
        }

        // When & Then
        assertTrue(config1.configEquals(config2))
    }

    @Test
    fun shouldBeEqualWhenSameReplicas() {
        // Given: first config with replicas
        config1.replicas.apply {
            addUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:123/db?user=user&password=password")
        }

        // And: second config with the same replicas
        config2.replicas.apply {
            addUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:123/db?user=user&password=password")
        }

        // When & Then
        assertTrue(config1.configEquals(config2))
    }

    @Test
    fun shouldNotBeEqualWhenReplicasDiffer() {
        // Given: first config with replicas
        config1.replicas.apply {
            addUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:123/db?user=user&password=password")
        }

        // And: second config with different replicas
        config2.replicas.apply {
            addUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:12356/db?user=user&password=password")
        }

        // When & Then
        assertFalse(config1.configEquals(config2))
    }

    @Test
    fun shouldNotBeEqualWhenDifferentAmountOfReplicas() {
        // Given: first config with replicas
        config1.replicas.apply {
            addUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:123/db?user=user&password=password")
        }

        // And: second config with more replicas
        config2.replicas.apply {
            addUri("jdbc:postgresql://192.168.100.1:1234/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:123/db?user=user&password=password")
            addUri("jdbc:postgresql://192.168.100.1:12356/db?user=user&password=password")
        }

        // When & Then
        assertFalse(config1.configEquals(config2))
    }
}