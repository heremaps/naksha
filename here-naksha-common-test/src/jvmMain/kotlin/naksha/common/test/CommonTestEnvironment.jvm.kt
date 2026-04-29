package naksha.common.test

internal actual fun currentEnvironment(): Map<String, String> = System.getenv()
