package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.text.Normalizer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.lowSurrogate;
import static naksha.base.JvmUtil.JVM_OBJECT_ARRAY_BASE_OFFSET;
import static naksha.base.JvmUtil.JVM_OBJECT_ARRAY_SCALE;
import static naksha.base.Platform.unsafe;

/**
 * String utils, including a cache with support for UTF-16 encoded Java strings, and UTF-8 encoded byte arrays.
 *
 * <p>The main purpose is to ensure that all strings are {@link Normalizer.Form#NFKC NFKC} normalized, and that keys being used within maps are interned. For the interning, this class contains a static string singleton cache to reduce memory consumption, and to improve the access speed for {@link JsonMap}, as it will only compare keys by reference, instead of the hash-codes, and then individual characters.
 *
 * <p>When keys or values are known to be repetitive <i>(some strings are known to appear very often)</i>, then they can be pinned, so that they are not garbage collected, even while currently no instance of them exist.
 * @since 3.0
 */
public final class StringUtil {
  /** The amount of bits to be used to the first level cache table. */
  private static final int BITS = 21;

  /** The mask for the hash to index in the first level. */
  private static final int MASK = (1 << BITS) - 1;

  /** The singleton for an empty string. */
  public static final String EMPTY_STRING = "";

  /**
   * The singletons for all strings that are just one code-point long in {@link Character#isBmpCodePoint(int) Basic Multilingual Plane (BMP)}.
   *
   * <p>Beware, the there is no string ({@code null}) in this array for those code-points that are surrogates.
   * @since 3.0
   */
  private static final @NotNull String @NotNull [] ONE_CHAR = new String[65536];
  static {
    for (int i = 0; i < ONE_CHAR.length; i++) {
      final char c = (char) i;
      ONE_CHAR[c] = !Character.isSurrogate(c) ? new String(Character.toChars(c)) : EMPTY_STRING;
    }
  }

  private static final class StringRef extends SoftReference<String> {
    public StringRef(String referent) {
      super(referent);
    }
  }

  /**
   * For every unique lower {@link #BITS bits} of a string hash-code, one {@link CacheLine CacheLine} will be kept in memory. It stores all weak-interned strings, and all pinned (strong-interned) strings. When a weak-referred string is removed by garbage collector, the next add or remove will compact the array.
   */
  private static class CacheLine extends AtomicReference<@Nullable Object @NotNull []> {
    private static final Object[] EMPTY = new Object[0];

    CacheLine() {
      super(EMPTY);
    }

    private static boolean matchesChars(@Nullable String s, char @NotNull [] chars, int start, int end, int hashCode) {
      if (s == null) return false;
      final var length = end - start;
      if (s.hashCode() != hashCode || s.length() != length) return false;
      for (int i = 0; i < length; i++) {
        if (s.charAt(i) != chars[start + i]) return false;
      }
      return true;
    }

    private static boolean matchesCharSeq(@Nullable String s, @NotNull CharSequence chars, int hashCode) {
      if (s == null) return false;
      if (s == chars) return true;
      final var length = chars.length();
      if (s.hashCode() != hashCode || s.length() != length) return false;
      for (int i = 0; i < length; i++) {
        if (s.charAt(i) != chars.charAt(i)) return false;
      }
      return true;
    }

    private static boolean matches(@Nullable String s, @NotNull Object charsOrSeq, int start, int end, int hashCode) {
      return charsOrSeq.getClass() == char[].class ? matchesChars(s, (char[]) charsOrSeq, start, end, hashCode) : matchesCharSeq(s, (CharSequence) charsOrSeq, hashCode);
    }

    private static @NotNull String newString(
        @NotNull Object charsOrSeq,
        int start,
        int end,
        boolean isNFKCNormalized
    ) {
      final String s;
      if (charsOrSeq.getClass() == char[].class) {
        s = new String((char[]) charsOrSeq, start, end - start);
      } else {
        assert charsOrSeq instanceof CharSequence;
        s = ((CharSequence) charsOrSeq).toString();
      }
      if (isNFKCNormalized || Normalizer.isNormalized(s, Normalizer.Form.NFKC)) {
        return s;
      }
      return Normalizer.normalize(s, Normalizer.Form.NFKC);
    }

    /// If `charsOrSeq` is `char[]`, then `start` and `end` are considered; otherwise they are ignored, and `charsOrSeq` must be `CharSequence`!
    private @NotNull String intern(@NotNull Object charsOrSeq, int start, int end, int hashCode, boolean isNFKCNormalized, boolean pin) {
      // Avoid generating the string and weak reference multiple times!
      String newString = null;
      StringRef newWeakString = null;
      do {
        // This is important.
        var array = this.getPlain();

        // Search for the given string, if it is cached already.
        // Remember the last empty slot, that we can use for insertion.
        // All algorithm will find the same slot, when iterating in parallel.
        int lastEmptyIndex = -1;
        Object lastEmptyValue = null;
        for (int i = 0; i < array.length; i++) {
          final Object raw = array[i];

          // Empty.
          if (raw == null) {
            lastEmptyIndex = i;
            lastEmptyValue = null;
            continue;
          }

          // Otherwise it must be either a pinned string, or a `StringRef`.
          final StringRef stringRef;
          final String s;
          if (raw.getClass() == String.class) {
            stringRef = null;
            s = (String) raw;
          } else {
            assert raw instanceof StringRef;
            stringRef = (StringRef) raw;
            s = stringRef.get();
          }

          // If the string is empty, it is garbage collected weak reference.
          // Still, eventually it is just an empty slot.
          if (s == null) {
            lastEmptyIndex = i;
            lastEmptyValue = stringRef;
            continue;
          }

          // If we have a cached string, but it is not what we are looking for, ignore.
          if (!matches(s, charsOrSeq, start, end, hashCode)) continue;

          // If we found the string, and it is pinned, we found what we were looking for, return it.
          if (stringRef == null) return s;

          // This is a matching weak-referred string, and we are not asked to pin, return it.
          if (!pin) return s;

          // This is a weak-referred string, but we are asked to pin it, so do this now.
          // It is possible that another thread expands the array while we update the weak-reference.
          // However, we know that others will not move the weak-reference, because it is still alive.
          // Therefore, we only ensure that we update the right array reference.
          do {
            array = this.getPlain();
            array[i] = s;
            unsafe.storeFence();
          } while (array != this.get());
          return s;
        }

        // String not found, create a new string, and optionally a weak-reference to it.
        if (newString == null) newString = newString(charsOrSeq, start, end, isNFKCNormalized);
        if (!pin && newWeakString == null) newWeakString = new StringRef(newString);

        // Ensure we have the right array reference, should another thread have expanded the array while we were trying to acquire the lock.
        final var current_array = this.get();

        // If there was an empty slot.
        if (lastEmptyIndex >= 0) {
          final long offset = JVM_OBJECT_ARRAY_BASE_OFFSET + lastEmptyIndex * JVM_OBJECT_ARRAY_SCALE;
          if (!unsafe.compareAndSwapObject(array, offset, lastEmptyValue, newWeakString != null ? newWeakString : newString)) {
            // Another thread updated used this slot, we need to restart.
            continue;
          }
          // CAS operations always intrinsically have full-fences, so we do not need yet another load-fence!
          if (array != this.getPlain()) {
            // Another thread replaces the whole array, so basically expanded the array. This means,
            // we can't be sure if he copied over our string or not, so we have to verify,
            // which means we need to restart.
            continue;
          }
          // We successfully wrote our change.
          return newString;
        }

        // There was no empty slot, we need to expand the array, to do this, we need to be sure that no
        // other thread has expanded the array already, if it has, we need to restart searching for
        // a free slot, there should be one.
        final var new_array = Json.ensure_size(array, array.length + 1, false, null);
        assert new_array.length > array.length;
        new_array[array.length] = newWeakString != null ? newWeakString : newString;
        if (this.compareAndSet(array, new_array)) {
          // Successfully expanded the array, and added our new value.
          return newString;
        }
        // Another thread expanded the array, restart and find an empty slot, there should now be one.
      } while (true);
    }

    private @Nullable String get(@NotNull Object charsOrSeq, int start, int end, int hashCode) {
      final var array = this.get();
      for (final Object raw : array) {
        if (raw == null) continue;

        final String s;
        if (raw.getClass() == String.class) {
          s = (String) raw;
        } else {
          assert raw instanceof WeakReference;
          //noinspection unchecked
          s = ((StringRef) raw).get();
        }

        if (s != null && matches(s, charsOrSeq, start, end, hashCode)) {
          return s;
        }
      }
      return null;
    }
  }

  /**
   * The concurrent cache, index is the lower 21-bit of hash-code of the string <i>(so 2,097,152 * 8 byte = 16 MiB)</i>.
   *
   * <p>Actually, each entry will allocate one {@link CacheLine CachedStringArray}, which means JVM header (16 byte) plus 8 byte for the reference to the {@code CachedString[]}, so 24 byte. If there are valid values in the array, then this itself is at least JVM header (16 byte), plus size (4 byte), plus padding (4 byte), plus 8 byte per entry (minimal 32 byte). Therefore, each entry is minimally (when null) 8 byte for the null-pointer, with one entry it is 56-bytes. So, if all entries contain one value, we allocate 112 MiB of memory, not considering the memory for the strings them self. With the strings, we can estimate around 250 MiB per one million strings.
   */
  private static final AtomicReferenceArray<@Nullable CacheLine> cacheTable = new AtomicReferenceArray<>(1 << BITS);

  // TODO: Should we allow to change the cache size, so reduce bits to reduce minimal memory consumption?
  //       It would require the application to do this when bootstrapping, then we can update the atomic reference array, swap it with a new one,
  //       copy over existing values, and re-distribute them. Its expensive, slow, but could be worth for applications that do not need the cache!

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
   * Calculates a standard hash-code above the given character sequence, only invokes {@code hashCode()} at the given character sequence, if it is a {@code String}; otherwise the hash-code is calculated manually the same way, that Java {@code String} does it.
   * @param chars the character sequence for which to calculate the hash-code.
   * @return the Java standard {@code String} hash-code.
   */
  public static int hashCodeOf(@NotNull CharSequence chars) {
    return hashCodeOf(chars, 0, chars.length());
  }

  /**
   * Calculates a standard hash-code above the given character sequence, only invokes {@code hashCode()} at the given character sequence, if it is a {@code String}; otherwise the hash-code is calculated manually the same way, that Java {@code String} does it.
   * @param chars the character sequence for which to calculate the hash-code.
   * @param fromIndex the index of the first character to consider.
   * @param toIndex the index of the first character <b>NOT</b> to consider.
   * @return the Java standard {@code String} hash-code.
   */
  public static int hashCodeOf(@NotNull CharSequence chars, int fromIndex, int toIndex) {
    if (chars.getClass() == String.class && fromIndex == 0 && toIndex == chars.length()) {
      return chars.hashCode();
    }
    int hashCode = 0;
    for (int i = fromIndex; i < toIndex; i++) {
      hashCode = hashChar(chars.charAt(i), hashCode);
    }
    return hashCode;
  }

  /**
   * Calculates a standard Java hash-code above the given character sequence.
   * @param chars the character sequence for which to calculate the hash-code.
   * @return the Java standard {@code String} hash-code.
   */
  public static int hashCodeOf(char @NotNull [] chars) {
    return hashCodeOf(chars, 0, chars.length);
  }

  /**
   * Calculates a standard Java hash-code above the given character sequence.
   * @param chars the character sequence for which to calculate the hash-code.
   * @param fromIndex the index of the first character to consider.
   * @param toIndex the index of the first character <b>NOT</b> to consider.
   * @return the Java standard {@code String} hash-code.
   */
  public static int hashCodeOf(char @NotNull [] chars, int fromIndex, int toIndex) {
    int hashCode = 0;
    for (int i = fromIndex; i < toIndex; i++) {
      hashCode = hashChar(chars[i], hashCode);
    }
    return hashCode;
  }

  /**
   * Returns the cached string singleton for the given character sequence or creates a new string instance. This method ensures that the returned string is {@link Normalizer.Form#NFKC NFKC} normalized. If possible, the method returns interned strings, but when there is no interned, it returns a new string.
   * @param charSequence the character sequence to normalize.
   * @return the normalized string.
   */
  public static @NotNull String normalize(@NotNull CharSequence charSequence) {
    final int length = charSequence.length();
    if (length == 0) return EMPTY_STRING;
    if (length == 1) return ONE_CHAR[charSequence.charAt(0)];

    final var cacheTable = StringUtil.cacheTable;
    final int hashCode = charSequence.getClass() == String.class ? charSequence.hashCode() : hashCodeOf(charSequence, 0, length);
    final int index = hashCode & MASK;
    final var cachedLine = cacheTable.getPlain(index);
    String string = cachedLine != null ? cachedLine.get(charSequence, -1, -1, hashCode) : null;
    if (string != null) return string;

    string = charSequence.toString();
    if (!Normalizer.isNormalized(string, Normalizer.Form.NFKC)) {
      string = Normalizer.normalize(string, Normalizer.Form.NFKC);
      final var existing = cachedLine != null ? cachedLine.get(string, -1, -1, hashCode) : null;
      if (existing != null) return existing;
    }
    return string;
  }

  /**
   * Returns the cached string singleton for the given characters or creates a new string instance, optionally ensures {@link Normalizer.Form#NFKC NFKC} form. This method does not intern the given string, but when there is an interned one, it returns it.
   * @param chars the characters.
   * @param fromIndex the first valid character.
   * @param toIndex the first invalid character, must be greater/equal than {@code start}, and less/equal to {@code chars.length}.
   * @param hashCode the Java hash code of the valid chars.
   * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; in doubt set to false.
   * @return the cached string or a new string.
   */
  public static @NotNull String newString(char @NotNull [] chars, int fromIndex, int toIndex, int hashCode, boolean isNFKCNormalized) {
    final int length = toIndex - fromIndex;
    if (length == 0) return EMPTY_STRING;
    if (length == 1) return ONE_CHAR[chars[fromIndex]];

    final var cacheTable = StringUtil.cacheTable;
    final int index = hashCode & MASK;
    final var cachedLine = cacheTable.getPlain(index);
    String string = cachedLine != null ? cachedLine.get(chars, fromIndex, toIndex, hashCode) : null;
    if (string != null) return string;

    string = new String(chars, fromIndex, toIndex);
    if (!isNFKCNormalized && !Normalizer.isNormalized(string, Normalizer.Form.NFKC)) {
      string = Normalizer.normalize(string, Normalizer.Form.NFKC);
      final var existing = cachedLine != null ? cachedLine.get(string, -1, -1, hashCode) : null;
      if (existing != null) return existing;
    }
    return string;
  }

  /**
   * Returns the interned string for a single character.
   * @param c the character for which to return a singleton.
   * @return the singleton.
   * @since 3.0
   */
  public static @NotNull String interned(char c) {
    return ONE_CHAR[c];
  }

  /**
   * Returns the cached string singleton for the given characters or {@code null}, if the characters are not yet cached. This method will return always null for character sequences not being in {@link Normalizer.Form#NFKC NFKC} form.
   * @param chars the characters.
   * @param fromIndex the first valid character.
   * @param toIndex the first invalid character, must be greater/equal than {@code start}, and less/equal to {@code chars.length}.
   * @param hashCode the Java hash code of the valid chars; if not known, use {@link #hashCodeOf(char[], int, int)} to calculate it.
   * @return the cached string or {@code null}, if no string cached.
   * @see #hashCodeOf(char[], int, int)
   */
  public static @Nullable String interned(char @NotNull [] chars, int fromIndex, int toIndex, int hashCode) {
    final int length = toIndex - fromIndex;
    if (length == 0) return EMPTY_STRING;
    if (length == 1) return ONE_CHAR[chars[fromIndex]];

    final int index = hashCode & MASK;
    final var cacheLine = cacheTable.getPlain(index);
    return cacheLine != null ? cacheLine.get(chars, fromIndex, toIndex, hashCode) : null;
  }

  /**
   * Returns the cached string singleton for the given characters or {@code null}, if the characters are not yet cached. This method will return always null for character sequences not being in {@link Normalizer.Form#NFKC NFKC} form.
   * @param charSequence the character sequence.
   * @return the cached string or {@code null}, if not cached.
   */
  public static @Nullable String interned(@NotNull CharSequence charSequence) {
    final int length = charSequence.length();
    if (length == 0) return EMPTY_STRING;
    if (length == 1) return ONE_CHAR[charSequence.charAt(0)];

    final int hashCode = hashCodeOf(charSequence);
    final int index = hashCode & MASK;
    final var cacheLine = cacheTable.getPlain(index);
    return cacheLine != null ? cacheLine.get(charSequence, -1, -1, hashCode) : null;
  }

  /**
   * Returns the interned string for a single character.
   * @param c the character for which to return a singleton.
   * @return the singleton.
   * @since 3.0
   */
  public static @NotNull String intern(char c) {
    return ONE_CHAR[c];
  }

  /**
   * Returns the string singleton for the given characters.
   * @param chars the characters.
   * @param start the first valid character.
   * @param end the first invalid character, must be greater/equal than {@code start}, and less/equal to {@code chars.length}.
   * @param hashCode the Java hash code over the characters; if not known, use {@link #hashCodeOf(char[], int, int)} to calculate it.
   * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; otherwise detection needed <i>(in doubt, always use false!)</i>.
   * @param pin if the string should be pinned, so prevent garbage collection, even while not used.
   * @return the string singleton.
   * @see #hashCodeOf(char[], int, int)
   */
  public static @NotNull String intern(char @NotNull [] chars, int start, int end, int hashCode, boolean isNFKCNormalized, boolean pin) {
    final int length = end - start;
    if (length == 0) return EMPTY_STRING;
    if (length == 1) return ONE_CHAR[chars[start]];

    final var cacheTable = StringUtil.cacheTable;
    final int index = hashCode & MASK;
    CacheLine cacheLine = cacheTable.getPlain(index);
    if (cacheLine != null) {
      return cacheLine.intern(chars, start, end, hashCode, isNFKCNormalized, pin);
    }
    cacheLine = new CacheLine();
    final var existing = cacheTable.compareAndExchange(index, null, cacheLine);
    return (existing != null ? existing : cacheLine).intern(chars, start, end, hashCode, isNFKCNormalized, pin);
  }

  /**
   * Returns the string singleton for the given character sequence.
   * @param charSequence The character sequence to turn into a string singleton.
   * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; otherwise detection needed <i>(in doubt, always use false!)</i>.
   * @param pin If the string should be pinned, so prevent garbage collection, even while not used.
   * @return the string singleton.
   */
  public static @NotNull String intern(@NotNull CharSequence charSequence, boolean isNFKCNormalized, boolean pin) {
    final int length = charSequence.length();
    if (length == 0) return EMPTY_STRING;
    if (length == 1) return ONE_CHAR[charSequence.charAt(0)];

    final int hashCode = hashCodeOf(charSequence);
    final var cache = StringUtil.cacheTable;
    final int index = hashCode & MASK;
    CacheLine cacheLine = cache.getPlain(index);
    if (cacheLine != null) {
      return cacheLine.intern(charSequence, -1, -1, hashCode, isNFKCNormalized, pin);
    }
    cacheLine = new CacheLine();
    final var existing = cache.compareAndExchange(index, null, cacheLine);
    return (existing != null ? existing : cacheLine).intern(charSequence, -1, -1, hashCode, isNFKCNormalized, pin);
  }

  /**
   * Returns the given string as singleton, {@link Normalizer.Form#NFKC NFKC} normalized version.
   * @param string The string to turn into a singleton.
   * @return the string singleton.
   * @since 3.0
   */
  public static @NotNull String intern(@NotNull String string) {
    return intern(string, false, false);
  }

  /**
   * Returns the given string as singleton, {@link Normalizer.Form#NFKC NFKC} normalized version, and permanently prevents that the string gets garbage collected.
   *
   * <p>This should be used for all strings that need to be statically cached:
   * <pre>{@code
   * package demo;
   * import naksha.base.StringUtil.pin;
   * class Demo extends JsonMapProxy<Object> {
   *   static final String _id = pin("id");
   *   static final String _name = pin("name");
   *   // ...
   *
   *   public @NotNull String getId() {
   *     final var raw = jsonMap().get(_id);
   *     if (raw instanceof String s) return s;
   *     throw new IllegalStateException("id is no string");
   *   }
   *
   *   public @Nullable String getName() {
   *     final var raw = jsonMap().get(_id);
   *     return raw instanceof String s ? s : null;
   *   }
   * }
   * }</pre>
   * Pinning a string releases burden from the garbage collector, and uses less memory, than dynamically interned strings.
   * @param string The string to turn into a singleton.
   * @return the string singleton.
   * @since 3.0
   */
  public static @NotNull String pin(@NotNull String string) {
    return intern(string, false, true);
  }
}