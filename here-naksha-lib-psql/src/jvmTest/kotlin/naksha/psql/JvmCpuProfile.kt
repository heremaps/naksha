package naksha.psql

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import jdk.jfr.Recording
import naksha.base.illegalState

/** Test-only JVM CPU/allocation counters and opt-in JFR sampling. No application configuration, environment, SQL parameters or feature contents are recorded. @since 3.0 */
internal object JvmCpuProfile {
  private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

  fun enableCounters() {
    if (!threads.isCurrentThreadCpuTimeSupported || !threads.isThreadAllocatedMemorySupported) throw illegalState("This JVM does not provide CPU/allocation counters")
    threads.isThreadCpuTimeEnabled = true
    threads.isThreadAllocatedMemoryEnabled = true
  }

  fun cpuNanos(): Long = threads.currentThreadCpuTime
  fun allocatedBytes(): Long = threads.getThreadAllocatedBytes(Thread.currentThread().id)

  fun recording(name: String): Recording? {
    val directory = System.getenv("NAKSHA_TEST_CPU_PROFILE_DIR") ?: return null
    val recording = Recording()
    recording.name = name
    recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5)).withStackTrace()
    recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(5)).withStackTrace()
    recording.enable("jdk.ObjectAllocationSample").withStackTrace()
    recording.enable("jdk.GarbageCollection")
    recording.enable("jdk.GCPhasePause")
    recording.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(100))
    recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1)).withStackTrace()
    recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1)).withStackTrace()
    recording.enable("jdk.SocketRead").withThreshold(Duration.ofMillis(1)).withStackTrace()
    // These events could reveal credentials/configuration. Explicitly exclude them even if a JVM defaults them on.
    recording.disable("jdk.InitialEnvironmentVariable")
    recording.disable("jdk.InitialSystemProperty")
    recording.destination = Path.of(directory).resolve("$name.jfr")
    Files.createDirectories(recording.destination.parent)
    recording.start()
    return recording
  }

  fun finish(recording: Recording?) {
    if (recording == null) return
    try { recording.stop() } finally { recording.close() }
  }
}
