package naksha.base;

import org.jetbrains.annotations.NotNull;

public final class Util {
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
}
