import org.gradle.api.Project

val minOverallCoverageKey: String = "minOverallCoverage"
val defaultOverallMinCoverage: Double = 0.8 // Don't decrease me!

fun Project.setOverallCoverage(minOverallCoverage: Double) {
    this.extensions.extraProperties.set(minOverallCoverageKey, minOverallCoverage)
}

fun Project.getOverallCoverage(): Double {
    return if (this.extensions.extraProperties.has(minOverallCoverageKey)) {
        this.extensions.extraProperties.get(minOverallCoverageKey) as? Double
            ?: throw IllegalStateException("Property '$minOverallCoverageKey' is expected to be Double")
    } else {
        defaultOverallMinCoverage
    }
}
