package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.lowSurrogate;

/**
 * Tooling around strings, supports Java strings (UTF-16 encoded) and UTF-8 encoded byte arrays.
 *
 * <p>This includes a string singleton cache, which should only be used for keys, because only there the advantage is huge, as it not only reduces memory consumption, but especially improves the hash access speed for {@link JsonMap}, as it will only compare references, instead of the hash-codes and the individual characters.
 *
 * <p>When keys or values are well known to be repetitive <i>(some strings are known to appear very often)</i>, then they can be interned and pinned, so that they are not garbage collected, even while currently no instance of them exist.
 */
public final class StringUtil {
  private static final int BITS = 21;

  /** The mask for the hash to index in the first level. */
  private static final int MASK = (1 << BITS) - 1;

  /** The singleton for an empty string. */
  public static final String EMPTY = "";

  /**
   * The singletons for all strings that are just one character long (there are more of them than one would expect).
   *
   * <p>Beware, the there is not string ({@code null}) in this array for those characters that are surrogates.
   */
  public static final @Nullable String @NotNull [] ONE_CHAR = new String[65536];
  static {
    for (int i = 0; i < ONE_CHAR.length; i++) {
      if (Character.isSurrogate((char)i)) continue;
      ONE_CHAR[i] = new String(Character.toChars(i));
    }
  }

  /**
   * A class to wrap each cached string, so adding 16-byte JVM header, 8-byte reference, 8-byte strong (pin) reference, and 4-byte hash-code, therefore 36 byte. Additionally, the string has to be kept on heap, until it is garbage collected, so another 16-byte JVM header, 8-byte byte-array reference, 4-byte hash-code, 1 byte coder, 1 byte hashIsZero boolean, 2 byte padding, therefore 32-byte. As it keeps a reference to the byte-array with the characters, this has again a JVM header of 16-byte, 4 byte length, plus characters, either ISO-8859-1 or UTF-16, so at least 24 byte.
   *
   * <p>In a nutshell, a cached string consumes minimally 92-byte (36 + 32 + 24), calculated for a string of length 1 to 4.
   */
  public final static class CachedString extends WeakReference<String> {
    private CachedString(@NotNull String s) {
      super(s);
      assert Normalizer.isNormalized(s, Normalizer.Form.NFKC);
      strong = s;
      hashCode = s.hashCode();
    }

    private final int hashCode;
    // Strong reference to the string, used to pin the string.
    private volatile @Nullable String strong;

    @Override
    public int hashCode() {
      return hashCode;
    }

    private @Nullable String getAndRelease() {
      String s = strong;
      if (s == null) s = get();
      strong = null;
      return s;
    }

    private @NotNull String pin() {
      String s = strong;
      if (s != null) return s;
      s = get();
      if (s == null) {
        throw new IllegalStateException("No strong reference to release");
      }
      strong = s;
      return s;
    }

    private @NotNull String release() {
      String s = strong;
      if (s == null) {
        throw new IllegalStateException("No strong reference to release");
      }
      strong = null;
      return s;
    }
  }

  private static @NotNull CachedString newCachedString(
      @Nullable CachedString existing,
      char @NotNull [] chars,
      int start,
      int end,
      boolean isNFKCNormalized
  ) {
    if (existing != null) {
      return existing;
    }
    var s = new String(chars, start, end - start);
    if (!isNFKCNormalized && !Normalizer.isNormalized(s, Normalizer.Form.NFKC)) {
      s = Normalizer.normalize(s, Normalizer.Form.NFKC);
    }
    return new CachedString(s);
  }

  private static @NotNull CachedString newCachedString(
      @Nullable CachedString existing,
      @NotNull CharSequence chars,
      boolean isNFKCNormalized
  ) {
    if (existing != null) {
      return existing;
    }
    var s = chars.toString();
    if (!isNFKCNormalized && !Normalizer.isNormalized(s, Normalizer.Form.NFKC)) {
      s = Normalizer.normalize(s, Normalizer.Form.NFKC);
    }
    return new CachedString(s);
  }

  /**
   * For every 21-bit unique lower bits of a string hash-code one {@code CachedStringArray} will be kept in memory. It consumes at least 16-byte JVM header, plus 8-byte {@code CachedString[]} reference (so 24 byte), plus the actual array, which consumes at least 16-byte JVM header, plus 4-byte size, plus 4-byte padding, plus 8-byte per stored {@code CachedString} reference (32-byte).
   *
   * <p>Therefore, for a size of zero, each {@code CachedStringArray} consume 48-byte, with one entry 56-byte. For one entry, we need to add the {@link CachedString CachedString} consumption, which is at least 96-byte, so each entry is at least 152-byte. A good number to keep in memory is, that each cached string costs around 192-byte to 256-byte, so caching one million strings, cost around 250 MiB of memory. This is only worth the effort, if the strings have really plenty of duplicates.
   */
  private static class CachedStringArray extends AtomicReference<@NotNull CachedString @NotNull []> {
    private static final CachedString[] EMPTY = new CachedString[0];
    private CachedStringArray() {
      super(EMPTY);
    }

    private boolean matches(@Nullable String s, char @NotNull [] chars, int start, int end, int hashCode) {
      if (s == null) return false;
      final var length = end - start;
      if (s.hashCode() != hashCode || s.length() != length) return false;
      for (int i = 0; i < length; i++) {
        if (s.charAt(i) != chars[start + i]) return false;
      }
      return true;
    }

    private boolean matches(@Nullable String s, @NotNull CharSequence chars, int hashCode) {
      if (s == null) return false;
      if (s == chars) return true;
      final var length = chars.length();
      if (s.hashCode() != hashCode || s.length() != length) return false;
      for (int i = 0; i < length; i++) {
        if (s.charAt(i) != chars.charAt(i)) return false;
      }
      return true;
    }

    /**
     * Returns the string singleton for the given characters.
     * @param chars the characters.
     * @param start the first valid character.
     * @param end the first invalid character, must be greater/equal than {@code start}, and less/equal to {@code chars.length}.
     * @param hashCode the Java hash code of the valid chars.
     * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; otherwise detection needed.
     * @param pin If the string should be pinned, so prevent garbage collection, even while not used.
     * @return the cached string or {@code null}, if no string cached.
     */
    private @NotNull String intern(char @NotNull [] chars, int start, int end, int hashCode, boolean isNFKCNormalized, boolean pin) {
      // We keep this out of the loop, so we only generate a new cached string ones, even when we encounter a concurrent modification.
      CachedString newCachedString = null;
      while (true) {
        final var cachedStrings = this.get();
        int nulls = 0;
        for (final CachedString cachedString : cachedStrings) {
          final var s = cachedString.get();
          if (s == null) {
            nulls++;
          } else {
            if (matches(s, chars, start, end, hashCode)) {
              return s;
            }
          }
        }
        // Not found, create a new string wrapper, which will be pinned initially.
        newCachedString = newCachedString(newCachedString, chars, start, end, isNFKCNormalized);
        // For now, no other thread has access, so release now or leave pinned, as requested.
        final var newString = pin ? newCachedString.pin() : newCachedString.release();
        var newArray = new CachedString[cachedStrings.length - nulls + 1];
        int i = 0;
        for (final CachedString cachedString : cachedStrings) {
          final var s = cachedString.get();
          if (s != null) {
            newArray[i++] = cachedString;
          }
        }
        newArray[i++] = newCachedString;
        if (i < newArray.length) { // GC happened in between, shorting, not that of a big issue
          newArray = Arrays.copyOf(newArray, i);
        }
        if (this.compareAndSet(cachedStrings, newArray)) {
          return newString;
        }
        // Concurrent modification, and we were slower, restart.
      }
    }

    /**
     * Returns the unique string instance for the given character sequence. If a string is given, a singleton of it is returned; can be the given string, or a new another instance.
     * @param chars The character sequence to turn into a string singleton.
     * @param hashCode the Java hash code over the character sequence.
     * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; otherwise detection is needed.
     * @param pin If the string should be pinned, so prevent garbage collection, even while not used.
     * @return the string singleton that represents the given character sequence.
     */
    private @NotNull String intern(@NotNull CharSequence chars, int hashCode, boolean isNFKCNormalized, boolean pin) {
      // We keep this out of the loop, so we only generate a new cached string ones, even when we encounter a concurrent modification.
      CachedString newCachedString = null;
      while (true) {
        final var cachedStrings = this.get();
        int nulls = 0;
        for (final CachedString cachedString : cachedStrings) {
          final var s = cachedString.get();
          if (s == null) {
            nulls++;
          } else {
            // We know that
            if (matches(s, chars, hashCode)) {
              if (pin) cachedString.pin();
              return s;
            }
          }
        }
        // Not found, create a new string wrapper, which will be pinned initially.
        newCachedString = newCachedString(newCachedString, chars, isNFKCNormalized);
        // For now, no other thread has access, so release now or leave pinned, as requested.
        final var newString = pin ? newCachedString.pin() : newCachedString.release();
        var newArray = new CachedString[cachedStrings.length - nulls + 1];
        int i = 0;
        for (final CachedString cachedString : cachedStrings) {
          final var s = cachedString.get();
          if (s != null) {
            newArray[i++] = cachedString;
          }
        }
        newArray[i++] = newCachedString;
        if (i < newArray.length) { // GC happened in between, shorting, not that of a big issue
          newArray = Arrays.copyOf(newArray, i);
        }
        if (this.compareAndSet(cachedStrings, newArray)) {
          return newString;
        }
        // Concurrent modification, and we were slower, restart.
      }
    }

    /**
     * Returns the cached string singleton for the given characters or {@code null}, if the characters are not yet cached.
     * @param chars the characters.
     * @param start the first valid character.
     * @param end the first invalid character, must be greater/equal than {@code start}, and less/equal to {@code chars.length}.
     * @param hashCode the Java hash code of the valid chars.
     * @return the cached string or {@code null}, if no string cached.
     */
    private @Nullable String get(char @NotNull [] chars, int start, int end, int hashCode) {
      var cachedStrings = this.get();
      for (final CachedString cachedString : cachedStrings) {
        final var s = cachedString.get();
        if (matches(s, chars, start, end, hashCode)) {
          return s;
        }
      }
      return null;
    }

    /**
     * Returns the cached string singleton for the given characters or {@code null}, if the characters are not yet cached.
     * @param chars the characters.
     * @param hashCode the hash-code of the character sequence.
     * @return the cached string or {@code null}, if no string cached.
     */
    private @Nullable String get(@NotNull CharSequence chars, int hashCode) {
      var cachedStrings = this.get();
      for (final CachedString cachedString : cachedStrings) {
        final var s = cachedString.get();
        if (matches(s, chars, hashCode)) {
          return s;
        }
      }
      return null;
    }
  }

  /**
   * The concurrent cache, index is the lower 21-bit of hash-code of the string <i>(so 2,097,152 * 8 byte = 16 MiB)</i>.
   *
   * <p>Actually, each entry will allocate one {@link CachedStringArray CachedStringArray}, which means JVM header (16 byte) plus 8 byte for the reference to the {@code CachedString[]}, so 24 byte. If there are valid values in the array, then this itself is at least JVM header (16 byte), plus size (4 byte), plus padding (4 byte), plus 8 byte per entry (minimal 32 byte). Therefore, each entry is minimally (when null) 8 byte for the null-pointer, with one entry it is 56-bytes. So, if all entries contain one value, we allocate 112 MiB of memory, not considering the memory for the strings them self. With the strings, we can estimate around 250 MiB per one million strings.
   */
  private static final AtomicReferenceArray<@Nullable CachedStringArray> cache = new AtomicReferenceArray<>(1 << BITS);

  /**
   * Updates the Java hash for the given code-point.
   * @param codePoint the code-point for which to update the hash.
   * @param hash the current Java hash, {@code 0} for the first hash.
   * @return the updated hash.
   */
  public static int hashCodePoint(int codePoint, int hash) {
    if (Character.isBmpCodePoint(codePoint)) {
      return hash * 31 + codePoint;
    }
    return (hash * 31 + highSurrogate(codePoint)) * 31 + lowSurrogate(codePoint);
  }

  /**
   * Updates the Java hash for the given UTF-16 code-unit.
   * @param c the UTF-16 code-unit for which to update the hash.
   * @param hash the current Java hash, {@code 0} for the first hash.
   * @return the updated hash.
   */
  public static int hashChar(char c, int hash) {
    return hash * 31 + c;
  }

  /**
   * Returns the cached string singleton for the given characters or {@code null}, if the characters are not yet cached.
   * @param chars the characters.
   * @param start the first valid character.
   * @param end the first invalid character, must be greater/equal than {@code start}, and less/equal to {@code chars.length}.
   * @param hashCode the Java hash code of the valid chars.
   * @return the cached string or {@code null}, if no string cached.
   */
  public static @Nullable String get(char @NotNull [] chars, int start, int end, int hashCode) {
    final var cache = StringUtil.cache;
    final int index = hashCode & MASK;
    final var cachedStrings = cache.getPlain(index);
    return cachedStrings != null ? cachedStrings.get(chars, start, end, hashCode) : null;
  }

  /**
   * Returns the cached string singleton for the given characters or creates a new string instance, and ensures {@link Normalizer.Form#NFKC NFKC} form. This method does not intern the given string, but when there is an interned one, it returns it.
   * @param chars the characters.
   * @param start the first valid character.
   * @param end the first invalid character, must be greater/equal than {@code start}, and less/equal to {@code chars.length}.
   * @param hashCode the Java hash code of the valid chars.
   * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; otherwise detection needed.
   * @return the cached string or {@code null}, if no string cached.
   */
  public static @NotNull String newString(char @NotNull [] chars, int start, int end, int hashCode, boolean isNFKCNormalized) {
    final var cache = StringUtil.cache;
    final int index = hashCode & MASK;
    final var cachedStrings = cache.getPlain(index);
    var string = cachedStrings != null ? cachedStrings.get(chars, start, end, hashCode) : null;
    if (string != null) return string;
    string = new String(chars, start, end);
    if (!isNFKCNormalized && !Normalizer.isNormalized(string, Normalizer.Form.NFKC)) {
      string = Normalizer.normalize(string, Normalizer.Form.NFKC);
      final var cached = cachedStrings != null ? cachedStrings.get(string, hashCode) : null;
      if (cached != null) return cached;
    }
    return string;
  }

  /**
   * Returns the cached string singleton for the given characters or {@code null}, if the characters are not yet cached.
   * @param chars the characters.
   * @return the cached string or {@code null}, if no string cached.
   */
  public static @Nullable String get(@NotNull CharSequence chars) {
    final int hashCode = chars.hashCode();
    final var cache = StringUtil.cache;
    final int index = hashCode & MASK;
    final var cachedStrings = cache.getPlain(index);
    return cachedStrings != null ? cachedStrings.get(chars, hashCode) : null;
  }

  /**
   * Returns the string singleton for the given characters.
   * @param chars the characters.
   * @param start the first valid character.
   * @param end the first invalid character, must be greater/equal than {@code start}, and less/equal to {@code chars.length}.
   * @param hashCode the Java hash code over the characters.
   * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; otherwise detection needed <i>(ib doubt, always select false!)</i>.
   * @param pin If the string should be pinned, so prevent garbage collection, even while not used.
   * @return the string singleton.
   */
  public static @NotNull String intern(char @NotNull [] chars, int start, int end, int hashCode, boolean isNFKCNormalized, boolean pin) {
    final var cache = StringUtil.cache;
    final int index = hashCode & MASK;
    CachedStringArray csa = cache.getPlain(index);
    if (csa != null) {
      return csa.intern(chars, start, end, hashCode, isNFKCNormalized, pin);
    }
    csa = new CachedStringArray();
    final var existing = cache.compareAndExchange(index, null, csa);
    if (existing != null) {
      return existing.intern(chars, start, end, hashCode, isNFKCNormalized, pin);
    }
    return csa.intern(chars, start, end, hashCode, isNFKCNormalized, pin);
  }

  /**
   * Returns the string singleton for the given character sequence.
   * @param chars The character sequence to turn into a string singleton.
   * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; otherwise detection needed <i>(ib doubt, always select false!)</i>.
   * @param pin If the string should be pinned, so prevent garbage collection, even while not used.
   * @return the string singleton.
   */
  public static @NotNull String intern(@NotNull CharSequence chars, boolean isNFKCNormalized, boolean pin) {
    final var hashCode = chars.hashCode();
    final var cache = StringUtil.cache;
    final int index = hashCode & MASK;
    CachedStringArray csa = cache.getPlain(index);
    if (csa != null) {
      return csa.intern(chars, hashCode, isNFKCNormalized, pin);
    }
    csa = new CachedStringArray();
    final var existing = cache.compareAndExchange(index, null, csa);
    if (existing != null) {
      return existing.intern(chars, hashCode, isNFKCNormalized, pin);
    }
    return csa.intern(chars, hashCode, isNFKCNormalized, pin);
  }

  /**
   * Returns the given string as singleton, {@link Normalizer.Form#NFKC NFKC} normalized version.
   *
   * <p>This should be used for all strings that are statically cached, for example:
   * <pre>{@code
   * package demo;
   * import naksha.base.StringUtil.intern;
   * class Demo {
   *   public static final SOME_CONST = intern("foo");
   * }
   * }</pre>
   * @param string The string to turn into a singleton.
   * @return the string singleton.
   */
  public static @NotNull String intern(@NotNull String string) {
    return intern(string, false, false);
  }

  /**
   * Returns the given string as singleton, {@link Normalizer.Form#NFKC NFKC} normalized version, and permanently prevents that the string gets garbage collected.
   *
   * <p>This should be used for all strings that need to be statically cached, but are not kept static somewhere:
   * <pre>{@code
   * package demo;
   * import naksha.base.StringUtil.intern;
   * class Demo {
   *   static void init() {
   *     pin("id");
   *     pin("properties");
   *     pin("geometry");
   *     pin("type");
   *     // ...
   *   }
   * }
   * }</pre>
   * @param string The string to turn into a singleton.
   * @return the string singleton.
   */
  public static @NotNull String pin(@NotNull String string) {
    return intern(string, false, true);
  }
}