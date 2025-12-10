package naksha.base;

import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Map;

/**
 * Utility class.
 */
public final class JvmUtil {
  /**
   * The size of a JVM pointer in byte.
   * @since 3.0
   */
  public static final int JVM_OOP_SIZE = Platform.unsafe.arrayIndexScale(Object[].class);

  /**
   * The size of the JVM header for an {@code Object[]}.
   * @since 3.0
   */
  public static final int JVM_OBJECT_ARRAY_HEADER_SIZE = Platform.unsafe.arrayBaseOffset(Object[].class);

  /**
   * The base-offset of the first element in an {@code Object[]}.
   * @since 3.0
   */
  public static final long JVM_OBJECT_ARRAY_BASE_OFFSET = JVM_OBJECT_ARRAY_HEADER_SIZE;

  /**
   * The size of each element within an {@code Object[]}.
   * @since 3.0
   */
  public static final long JVM_OBJECT_ARRAY_SCALE = JVM_OOP_SIZE;

  /**
   * The size of a L1 cache line.
   * @since 3.0
   */
  public static final int JVM_L1_CACHE_LINE_SIZE = 64;

  /**
   * A method to calculate the optimal length of an {@code Object[]}, so that it perfectly fits into L1 cache lines, not wasting any space. This has the nice side effect to prevent false sharing.
   * @param amount the amount of references that should be stored minimal <i>(for a map, 2 references are needed for each entry, key and value)</i>.
   * @return the optimal length for an {@code Object[]} to store at least the given amount of references.
   */
  public static int optimalObjectArrayLength(int amount) {
    // The want the Object[] to fit exactly into L1 cache lines.
    // Calculate how many bytes we need to allocate minimally, when the want to store exactly the requested amount of oops.
    var new_size = JVM_OBJECT_ARRAY_HEADER_SIZE + amount * JVM_OOP_SIZE;
    // Now, calculate how many bytes we need to allocate, to keep the new_size exactly in L1 cache lines.
    new_size = (new_size + JVM_L1_CACHE_LINE_SIZE - 1) & (-JVM_L1_CACHE_LINE_SIZE);
    // Now, we deduct the mount of OOPs (array-length) that will fit into the allocated memory.
    // So, subtract the JVM header, divide the rest by the OOP size.
    // This results in the optimal array-length, so the amount of OOPs we can store in the allocated memory.
    final var optimal_length = (new_size - JVM_OBJECT_ARRAY_HEADER_SIZE) / JVM_OOP_SIZE;
    assert new_size == JVM_OBJECT_ARRAY_HEADER_SIZE + optimal_length * JVM_OOP_SIZE;
    assert amount <= optimal_length;
    return optimal_length;
  }

  /**
   * The amount of oops that can be stored in an L1 cache line.
   * @since 3.0
   */
  public static final int JVM_OOPS_PER_CACHE_LINE = JVM_L1_CACHE_LINE_SIZE / JVM_OOP_SIZE;

  /**
   * Stores the minimal capacity an array with one element will have to exactly fit into one L1 cache line.
   * @since 3.0
   */
  public static final int JVM_MIN_CAPACITY = optimalObjectArrayLength(1);

  /**
   * A hint if the application is running in debugger. This is important, because IntelliJ debugger materializes {@code Map.Entry} values incorrectly, violating the Java spec (which allows {@code Map.entrySet()} entries to be backed by the map), it should invoke {@link java.util.Map.Entry#copyOf(Map.Entry)}, but doesnt.
   * @since 3.0
   */
  public static boolean JVM_DEBUGGING;
  static {
    final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    final var args = runtime.getInputArguments();
    boolean isDebugging = false;
    for (final String arg : args) {
      if (arg.contains("-agentlib:jdwp")) {
        isDebugging = true; // JDWP debugger (e.g., IntelliJ, Eclipse, NetBeans)
        break;
      }
    }
    JVM_DEBUGGING = isDebugging;
  }

  /** The {@code MSS} <i>(Maximum Segment Size)</i> in {@code TCP} for ethernet frames. */
  public static final int Ethernet_Mss = 1500;

  /** The {@code MSS} <i>(Maximum Segment Size)</i> in {@code TCP} for PPPoE <i>(Point-to-Point Protocol over Ethernet)</i> frames. */
  public static final int PPPoE_Mss = 1452;

  /** The {@code MSS} <i>(Maximum Segment Size)</i> in {@code TCP} for JUMBO ethernet frames (as used within AWS). */
  public static final int Jumbo_Mss = 8960;

  /**
   * Calculates the optimal TCP receive/send buffer size.
   *
   * @param maxBitsPerSecond the maximal bits per second needed, for example 100_000_000 for 100 Mbps.
   * @param latencyMicros the amount of microseconds that a package is estimated to travel ({@code 1,000,000}'th of a second).
   * @return the optimal size for the socket buffer to reach the desired throughput.
   * @throws IllegalArgumentException if the given latency or max bits are less or equal zero.
   * @since 3.0
   */
  public static int optimalTcpBufferSize(int maxBitsPerSecond, int latencyMicros)
      throws IllegalArgumentException
  {
    return optimalTcpBufferSize(maxBitsPerSecond, latencyMicros, PPPoE_Mss);
  }

  /**
   * Calculates the optimal TCP receive/send buffer size.
   *
   * @param maxBitsPerSecond the maximal bits per second needed, for example 100_000_000 for 100 Mbps.
   * @param latencyMicros the amount of microseconds that a package is estimated to travel ({@code 1,000,000}'th of a second).
   * @param MSS the {@code MSS} <i>(Maximum Segment Size)</i> size.
   * @return the optimal size for the socket buffer to reach the desired throughput.
   * @throws IllegalArgumentException if the given latency or max bits are less or equal zero.
   * @since 3.0
   */
  public static int optimalTcpBufferSize(int maxBitsPerSecond, int latencyMicros, int MSS)
      throws IllegalArgumentException
  {
    if (latencyMicros <= 0 || maxBitsPerSecond <= 0) {
      throw new IllegalArgumentException();
    }
    // The maximum segment size (often as well referred to as MTU, maximum transmission unit).
    // Normally everybody uses the ethernet default frame, which is 1500, minus the headers we are left with 1452 to 1460 bytes.
    // To allow a fast recovery we should not set the reception buffer smaller than 16 times the MSS.
    final int MIN_SIZE = MSS << 4; // << 4 == * 16, just faster
    // We have 16 bit in the TCP header (0-65535) for the window size with an extension option that multiplies this by 2^14.
    // It makes no sense to allocate a bigger receive buffer anyway.
    final int MAX_SIZE = 1_073_725_440; // = 65535 * 16384 = 1_073_725_440 (~ 1gb)
    final double byte_size = (((double) maxBitsPerSecond) / (1_000_000d / (double) latencyMicros)) / 8;
    if (byte_size < MIN_SIZE) {
      return MIN_SIZE;
    }
    if (byte_size > MAX_SIZE) {
      return MAX_SIZE;
    }
    // We should set the reception buffer size to a multiple of the MSS; if possible.
    final int isize = (int) byte_size;
    final int size = (isize / MSS) * MSS;
    return size == isize ? size : size + MSS;
  }

  /**
   * Ensures that the value is not null.
   *
   * @param value the value, may be null.
   * @param alternative the alternative to return, when the value is null.
   * @param <T> the type.
   * @return either value or alternative, if value is null.
   * @since 3.0
   */
  @NotNull
  public static <T> T valueOr(final T value, final @NotNull T alternative) {
    return value != null ? value : alternative;
  }

  /**
   * Returns the next best 2^n number for the given size.
   *
   * @param size the size to be extended to the next best 2^n number. If being negative or zero, then 1 will be returned.
   * @return the next best 2^n number with "n" being at least 0.
   * @since 3.0
   */
  public static long highest2UpNOf(long size) {
    return size < 2 ? 1L : Long.highestOneBit(size - 1) << 1;
  }

  /**
   * Returns the next best 2^n number for the given size.
   *
   * @param size the size to be extended to the next best 2^n number. If being negative or zero, then 1 will be returned.
   * @return the next best 2^n number with "n" being at least 0.
   * @since 3.0
   */
  public static int highest2UpNOf(int size) {
    return size < 2 ? 1 : Integer.highestOneBit(size - 1) << 1;
  }

  /**
   * Returns the lower 32-bit integer, assuming it is an integer (keep the sign bit).
   *
   * @param value the 64 bit long.
   * @return the lower 32-bit integer.
   * @since 3.0
   */
  public static int lowInt(final long value) {
    return (int) (value & 0xffffffffL);
  }

  /**
   * Returns the higher 32-bit integer, assume it is an integer (keep the sign bit).
   *
   * @param value the 64 bit long.
   * @return the higher 32-bit integer.
   * @since 3.0
   */
  public static int highInt(final long value) {
    return (int) (value >>> 32);
  }

  /**
   * Combine two integers into one long.
   *
   * @param high the integer to be stored in the higher 32 bit of the long.
   * @param low the integer to be stored in the lower 32 bit of the long.
   * @return the combined long.
   * @since 3.0
   */
  public static long combineInts(int high, int low) {
    return (((long) high) << 32) | low;
  }

  /**
   * Convert the given value into <a href="https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/">zigzag</a> encoding.
   * @param value the value.
   * @return the <a href="https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/">zigzag</a> encoded value.
   * @since 3.0
   */
  public static long toZigZag(long value) {
    return (value << 1) ^ (value >>> 63);
  }

  /**
   * Convert the given value into <a href="https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/">zigzag</a> encoding.
   * @param value the value.
   * @return the <a href="https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/">zigzag</a> encoded value.
   * @since 3.0
   */
  public static int toZigZag(int value) {
    return (value << 1) ^ (value >>> 31);
  }

  /**
   * Convert the given <a href="https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/">zigzag</a> encoded value back to normal.
   * @param value the <a href="https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/">zigzag</a> encoded value.
   * @return the normal value.
   * @since 3.0
   */
  public static long fromZigZag(long value) {
    return (value >>> 1) ^ -(value & 1);
  }

  /**
   * Convert the given <a href="https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/">zigzag</a> encoded value back to normal.
   * @param value the <a href="https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/">zigzag</a> encoded value.
   * @return the normal value.
   * @since 3.0
   */
  public static int fromZigZag(int value) {
    return (value >>> 1) ^ -(value & 1);
  }
}
