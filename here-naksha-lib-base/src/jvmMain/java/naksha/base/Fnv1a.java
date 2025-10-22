package naksha.base;

/**
 * Java methods to generate an 32-bit <a href="https://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function">FNV1a hash</a>.
 */
@SuppressWarnings("unused")
public final class Fnv1a {
  /** The multiplication prime for 32-bit hashes. */
  private static final int MUL32 = 16777619;

  /** The multiplication prime for 64-bit hashes. */
  private static final long MUL64 = 1099511628211L;

  /** The initial 32-bit hash value. */
  public static final int START32 = 0x811C9DC5;

  /** The 64-bit initial hash value. */
  public static final long START64 = 0xCBF29CE484222325L;

  /**
   * Hash a single byte.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @return the updated hash.
   */
  public static int hash32_byte(int hashCode, byte v) {
    return (hashCode * MUL32) ^ (v & 0xff);
  }

  /**
   * Hash a single byte.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @return the updated hash.
   */
  public static long hash64_byte(long hashCode, byte v) {
    return (hashCode * MUL64) ^ (v & 0xffL);
  }

  /**
   * Hash a short.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static int hash32_short(int hashCode, short v, boolean le) {
    final int wide = (le ? Short.reverseBytes(v) : v) & 0xffff;
    hashCode = (hashCode * MUL32) ^ (wide >>> 8);
    hashCode = (hashCode * MUL32) ^ (wide & 0xff);
    return hashCode;
  }

  /**
   * Hash a short.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static long hash64_short(long hashCode, short v, boolean le) {
    final long wide = (le ? Short.reverseBytes(v) : v) & 0xffffL;
    hashCode = (hashCode * MUL64) ^ (wide >>> 8);
    hashCode = (hashCode * MUL64) ^ (wide & 0xffL);
    return hashCode;
  }

  /**
   * Hash a UNICODE code-point <i>(code points are logically always encoded in big-endian)</i>.
   * @param hashCode The current hash code.
   * @param cp The code-point.
   * @return the updated hash.
   */
  public static int hash32_codePoint(int hashCode, int cp){
    hashCode = (hashCode * MUL32) ^ ((cp >>> 16) & 0xff);
    hashCode = (hashCode * MUL32) ^ ((cp >>> 8) & 0xff);
    hashCode = (hashCode * MUL32) ^ (cp & 0xff);
    return hashCode;
  }

  /**
   * Hash a UNICODE code-point <i>(code points are logically always encoded in big-endian)</i>.
   * @param hashCode The current hash code.
   * @param cp The code-point.
   * @return the updated hash.
   */
  public static long hash64_codePoint(long hashCode, int cp){
    hashCode = (hashCode * MUL64) ^ ((cp >>> 16) & 0xffL);
    hashCode = (hashCode * MUL64) ^ ((cp >>> 8) & 0xffL);
    hashCode = (hashCode * MUL64) ^ (cp & 0xffL);
    return hashCode;
  }

  /**
   * Hash an integer.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static int hash32_int(int hashCode, int v, boolean le){
    final int wide = le ? Integer.reverseBytes(v) : v;
    hashCode = (hashCode * MUL32) ^ (wide >>> 24);
    hashCode = (hashCode * MUL32) ^ ((wide >>> 16) & 0xff);
    hashCode = (hashCode * MUL32) ^ ((wide >>> 8) & 0xff);
    hashCode = (hashCode * MUL32) ^ (wide & 0xff);
    return hashCode;
  }

  /**
   * Hash an integer.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static long hash64_int(long hashCode, int v, boolean le){
    final long wide = (le ? Integer.reverseBytes(v) : v) & 0xffff_ffffL;
    hashCode = (hashCode * MUL64) ^ (wide >>> 24);
    hashCode = (hashCode * MUL64) ^ ((wide >>> 16) & 0xffL);
    hashCode = (hashCode * MUL64) ^ ((wide >>> 8) & 0xffL);
    hashCode = (hashCode * MUL64) ^ (wide & 0xffL);
    return hashCode;
  }

  /**
   * Hash a long.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static int hash32_long(int hashCode, long v, boolean le){
    final long wide = le ? Long.reverseBytes(v) : v;
    hashCode = (hashCode * MUL32) ^ (int)(wide >>> 56);
    hashCode = (hashCode * MUL32) ^ (int)((wide >>> 48) & 0xffL);
    hashCode = (hashCode * MUL32) ^ (int)((wide >>> 40) & 0xffL);
    hashCode = (hashCode * MUL32) ^ (int)((wide >>> 32) & 0xffL);
    hashCode = (hashCode * MUL32) ^ (int)((wide >>> 24) & 0xffL);
    hashCode = (hashCode * MUL32) ^ (int)((wide >>> 16) & 0xffL);
    hashCode = (hashCode * MUL32) ^ (int)((wide >>> 8) & 0xffL);
    hashCode = (hashCode * MUL32) ^ (int)(wide & 0xffL);
    return hashCode;
  }

  /**
   * Hash a long.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static long hash64_long(long hashCode, long v, boolean le){
    final long wide = le ? Long.reverseBytes(v) : v;
    hashCode = (hashCode * MUL64) ^ (wide >>> 56);
    hashCode = (hashCode * MUL64) ^ ((wide >>> 48) & 0xffL);
    hashCode = (hashCode * MUL64) ^ ((wide >>> 40) & 0xffL);
    hashCode = (hashCode * MUL64) ^ ((wide >>> 32) & 0xffL);
    hashCode = (hashCode * MUL64) ^ ((wide >>> 24) & 0xffL);
    hashCode = (hashCode * MUL64) ^ ((wide >>> 16) & 0xffL);
    hashCode = (hashCode * MUL64) ^ ((wide >>> 8) & 0xffL);
    hashCode = (hashCode * MUL64) ^ (wide & 0xffL);
    return hashCode;
  }

  /**
   * Hash a float.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static int hash32_float(int hashCode, long v, boolean le){
    return hash32_int(hashCode, Float.floatToIntBits(v), le);
  }

  /**
   * Hash a float.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static long hash64_float(long hashCode, long v, boolean le){
    return hash64_int(hashCode, Float.floatToIntBits(v), le);
  }

  /**
   * Hash a double.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static int hash32_double(int hashCode, double v, boolean le){
    return hash32_long(hashCode, Double.doubleToLongBits(v), le);
  }

  /**
   * Hash a double.
   * @param hashCode The current hash code.
   * @param v The value to hash.
   * @param le if the hash should be calculated for little-endian byte-order.
   * @return the updated hash.
   */
  public static long hash64_double(long hashCode, double v, boolean le){
    return hash64_long(hashCode, Double.doubleToLongBits(v), le);
  }
}