package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;

import static ch.randelshofer.fastdoubleparser.JsonDoubleParser.parseDouble;
import static java.lang.Character.isWhitespace;
import static naksha.base.UTF8.*;

/**
 * A fast low-level JSON parser. This class is very low-level, it actually parses a given UTF-8 encoded JSON string into one of the following types:
 * <ul>
 * <li>{@code null} -> {@code null}
 * <li>{@code Boolean} -> {@link Boolean}
 * <li>{@code Number} -> {@link Number}: Actually always results in {@link Long} or {@link Double}, they will be interned with some limits.
 * <li>{@code String} -> {@link String} or {@link JsonKey}: The returned strings are {@link NormalizerForm#NFKC NFKC} normalized, and if being keys in a map, they are interned using {@link JsonKey}. The interning guarantees that there will never be the same key twice in memory, which allows to compare keys by reference, basically you are fine to do {@code key1 == key2}. This makes maps much faster.
 * <li>{@code Array} -> {@link JsonArray}
 * <li>{@code Map} -> {@link JsonMap}
 * <li>{@code UInt8Array} -> {@code byte[]}: In a JSON typed-arrays are encoded as <a href="https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data">data-url</a>, like {@code data:UInt8Array;base64,<data>}.
 * <li>{@code Int16Array} -> {@code short[]}: In a JSON typed-arrays are encoded as <a href="https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data">data-url</a>, like {@code data:Int16Array;base64,<data>}.
 * <li>{@code Int32Array} -> {@code int[]}: In a JSON typed-arrays are encoded as <a href="https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data">data-url</a>, like {@code data:Int32Array;base64,<data>}.
 * <li>{@code BigInt64Array} -> {@code long[]}: In a JSON typed-arrays are encoded as <a href="https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data">data-url</a>, like {@code data:BigInt64Array;base64,<data>}.
 * <li>{@code Float32Array} -> {@code float[]}: In a JSON typed-arrays are encoded as <a href="https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data">data-url</a>, like {@code data:Float32Array;base64,<data>}.
 * <li>{@code Float64Array} -> {@code double[]}: In a JSON typed-arrays are encoded as <a href="https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data">data-url</a>, like {@code data:Float64Array;base64,<data>}.
 * </ul>
 * Beware that the encoding of binary data is a none-standard extension, only supported by this JSON parser. It as well allows comments and keys or values to be not quoted.
 *
 * <h1>Implementation Notes</h1>
 * Internally, the JSON parser does encode {@code Array} as {@code Object[]}, so while parsing. The array encodes in the first slot the type, in the second the reference to the parent, followed by all elements, eventually ended either by the and of the array or with a special {@link #END} element, when the list does not use all slots. This reduces the amount of resized that need to be done.
 *
 * <p>The same is true for {@code Map}, as well encoded in {@code Object[]}, with the first two slots used for TYPE and parent reference, plus then additional two slots for each map-entry, storing all keys as ({@link JsonKey}), and values as one of the valid Json types documented above. The array is eventually ended with a special {@link #END} key, when not all slots are used to encode map-entries.
 *
 * <p>Technically, the parser will work optimal, when the JSON is not too deep, and only has small objects; huge objects with hundreds of key-value pairs or elements in an array, are rather bad for the performance.
 * @since 3.0
 */
public final class JsonParser {

  /**
   * The thread local parser instance to be used to reduce memory consumption.
   * @since 3.0
   */
  public static final ThreadLocal<@NotNull JsonParser> instance = ThreadLocal.withInitial(JsonParser::new);

  /**
   * The index of the object type in low-level representation <i>({@code Object[]})</i>.
   * @since 3.0
   */
  public static final int TYPE = 0;

  /**
   * The value to signal {@link JsonMap} type.
   * @since 3.0
   */
  public static final Object MAP = new Object();

  /**
   * The value to signal {@link JsonArray} type.
   * @since 3.0
   */
  public static final Object ARRAY = new Object();

  /**
   * The index of the object state in low-level representation <i>({@code Object[]})</i>.
   *
   * <p>Used while parsing to keep a reference to the parent, as long as the object is just yet partially processed, when going back to parent, it is replaced with state.
   * @since 3.0
   */
  public static final int STATE = 1;

  /**
   * The value to signal that the object is empty <i>(has no valid elements or map entries)</i>.
   * @since 3.0
   */
  public static final Object IS_EMPTY = new Object();

  /**
   * The value to signal that the object is full <i>(all slots store valid elements or map entries)</i>. This means, the length of the object can be calculated from the length of the {@code Object[]}.
   * @since 3.0
   */
  public static final Object IS_FULL = new Object();

  /**
   * The index of the first value element or map-entry in low-level representation <i>({@code Object[]})</i>.
   * @since 3.0
   */
  public static final int FIRST = 2;

  /**
   * The END sign, when the object slots are not fully used.
   * @since 3.0
   */
  public static final Object END = new Object();

  /**
   * An empty array.
   * @since 3.0
   */
  public static final Object[] EMPTY_ARRAY = new Object[0];

  /**
   * An empty map.
   * @since 3.0
   */
  public static final Object[] EMPTY_MAP = new Object[0];

  /**
   * Tests if the given data array represents an array.
   * @param data the data array.
   * @return true if this represents an array.
   */
  public static boolean isArray(Object @NotNull [] data) {
    return data == EMPTY_ARRAY || data.length > 0 && data[0] == ARRAY;
  }

  /**
   * Tests if the given data array represents a map.
   * @param data the data array.
   * @return true if this represents a map.
   */
  public static boolean isMap(Object @NotNull [] data) {
    return data == EMPTY_MAP || data.length > 0 && data[0] == MAP;
  }

  /**
   * Tests if the given map or array data is empty.
   * @param data The map or array to test.
   * @return true if the map or array is empty.
   */
  public static boolean isEmpty(Object @NotNull [] data) {
    final int length = data.length;
    if (length == 0) return true;
    assert length >= 6;
    if (data[1] == IS_EMPTY) return true;
    if (data[1] == IS_FULL) return false;
    // In any case, when the array is empty, we will have an END mark at the first slot!
    return data[2] == END;
  }

  /**
   * Tests if the given map or array data is full.
   * @param data The map or array to test.
   * @return true if the map or array is full.
   */
  public static boolean isFull(Object @NotNull [] data) {
    final int length = data.length;
    if (length == 0) return true;
    assert length >= 6;
    if (data[1] == IS_EMPTY) return false;
    if (data[1] == IS_FULL) return true;
    if (data[2] == END) return false; // isEmpty!
    if (data[0] == MAP) {
      var end = 4;
      while (end < length && data[end] != END) end += 2;
      return end == length;
    }
    // ARRAY
    var end = 3;
    while (end < length && data[end] != END) end++;
    return end == length;
  }

  /**
   * Returns the amount of elements in an array or the amount of entries in a map.
   *
   * @param data the map or array.
   * @return the amount of valid elements or entries.
   */
  public static int lengthOf(Object @NotNull [] data) {
    final int length = data.length;
    if (length == 0) return 0;
    assert length >= 6;
    if (data[1] == IS_EMPTY) return 0;
    if (data[0] == MAP) {
      if (data[1] == IS_FULL) return (length - 2) >> 1;
      var end = 2;
      while (end < length && data[end] != END) end += 2;
      return (end - 2) >> 1;
    }
    // ARRAY
    if (data[1] == IS_FULL) return length - 2;
    var end = 2;
    while (end < length && data[end] != END) end++;
    return end - 2;
  }

  /**
   * Returns the amount of elements or entries that can be stored in the given map or array.
   * @param data The map or array data.
   * @return The amount of elements or entries that can be stored in the given map or array.
   */
  public static int capacityOf(Object @NotNull [] data) {
    if (data.length == 0) return 0;
    assert data.length >= 6;
    if (data[0] == MAP) return (data.length - 2) >> 1;
    return data.length - 2;
  }

  /**
   * A method optimized for 64-bit CPU, and for small objects/arrays as we normally encounter in JSON. It is unusual in JSON, to ever encounter a map or array with more than 20 elements. So, we optimize for memory consumption and CPU caching of small data structures here.
   * @param data the data to ensure a specific capacity.
   * @param capacity the amount of values that should be stored (if map-entries, then 2 values are needed for each entry).
   * @param shrink if shrinking is wanted.
   * @return either the given array or a new one with the data copied over, so that the new values are fitting into.
   */
  public static Object @NotNull [] ensureCapacity(Object @NotNull [] data, int capacity, boolean shrink) {
    // Handle empty
    if (capacity < 0) throw new IllegalArgumentException("Capacity must not be negative");
    if (capacity == 0) {
      if (!shrink) return data;
      if (isMap(data)) return EMPTY_MAP;
      return EMPTY_ARRAY;
    }
    capacity += 2; // the first two slots are internally used, and must always be there.
    if (data.length == capacity) return data;

    // The want the Object[] to fit exactly into L1 cache lines.
    // Therefore, the first "page" can only hold 6 values, every following each 8.
    // The reason is that each value is a pointer, and we optimize this for 64-bit machines with no pointer compression!
    // - If we request 1 to 6 values, we expect the result to be 6.
    // - If we request 7 values, we expect the result to be 8 + 6 = 14.
    // - If we request 14 values, we expect the result to be 8 + 6 = 14.
    // - If we request 15 values, we expect the result to be 16 + 6 = 24.
    var new_size = (((capacity-6) + 7) & 0xffff_fff8) + 6;
    if (data.length == new_size) { // unchanged size (actually, there is still room left).
      return data;
    }
    if (new_size > data.length) { // we need to expand.
      var new_data = new Object[new_size];
      System.arraycopy(data, 0, new_data, 0, data.length);
      return new_data;
    }
    if (!shrink) return data;
    // We need to shrink.
    var new_data = new Object[new_size];
    System.arraycopy(data, 0, new_data, 0, new_size);
    return new_data;
  }

  // Allocate 2 KiB for the stack (3 elements are used for the JVM header, size, and padding on 64-bit JVM).
  // We stick with this stack for the life-time of the parser.
  private final @Nullable Object @NotNull[] @Nullable[] stack = new Object[256-3][];
  private int stack_end;
  private ThreadLocalCharBuffer charBuffer;
  private char[] chars;
  private int chars_end;
  private int chars_hash;
  private boolean isNFKCNormalized;
  private int line;
  private int column;
  private int i;

  /**
   * To be called ones a line comment has hit, so after reading {@code //}.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first byte after {@code \n}.
   * @return the index of the first byte after the comment ends.
   */
  private int skipLineComment(byte[] utf8, int i) {
    int line = this.line;
    int column = this.column;
    try {
      int cp;
      do {
        final var result = decodeCodePoint(utf8, i);
        cp = resultCodePoint(result);
        i = resultNextIndex(result);
        if (cp == '\n') {
          line++;
          column = 0;
        } else {
          column++;
        }
      } while (cp != '\n');
      return i;
    } finally {
      this.line = line;
      this.column = column;
    }
  }

  /**
   * To be called ones a line comment has hit, so after reading <code>/*</code>.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first byte after <code>*\</code>.
   * @return the index of the first byte after the comment ends.
   */
  private int skipCommentBlock(byte[] utf8, int i) {
    int line = this.line;
    int column = this.column;
    try {
      int prev_cp;
      int cp = -1;
      while (true) {
        prev_cp = cp;
        final var result = decodeCodePoint(utf8, i);
        cp = resultCodePoint(result);
        i = resultNextIndex(result);
        if (cp == '\n') {
          line++;
          column = 0;
        } else {
          column++;
        }
        if (prev_cp == '*' && cp == '/') {
          // Refers to the first byte after comment end.
          return i;
        }
      }
    } finally {
      this.line = line;
      this.column = column;
    }
  }

  private static final boolean[] WHITESPACES = new boolean[256];
  static {
    for (int i = 0; i <= 32; i++) {
      WHITESPACES[i] = true;
    }
    WHITESPACES[','] = true;
    WHITESPACES[':'] = true;
  }

  /**
   * Skip all white-spaces, including {@code ,}, {@code ;}, and {@code :}, next to line-feed <code>\n</code>, carriage-return <code>\r</code> and others.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading.
   * @return the index of the first byte that is no white-space.
   */
  private int skipWhiteSpaces(byte[] utf8, int i) {
    int line = this.line;
    int column = this.column;
    try {
      final var WHITESPACES = JsonParser.WHITESPACES;
      int cp;
      while (true) {
        final var result = decodeCodePoint(utf8, i);
        cp = resultCodePoint(result);
        assert cp >= 0;
        if (cp >= WHITESPACES.length || !WHITESPACES[cp]) {
          // Use the current "i", because this code-point is basically the search none-whitespace.
          // So, ignore next-index.
          return i;
        }
        if (cp == '\n') {
          line++;
          column = 0;
        } else {
          column++;
        }
        i = resultNextIndex(result);
      }
    } finally {
      this.line = line;
      this.column = column;
    }
  }

  private Object parsedValue;
  private boolean potentialLong;
  private boolean potentialDouble;

  /**
   * Parse a quoted string, store the result in {@link #parsedValue}.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first byte after the {@code startChar}.
   * @param startChar the character the string started with ({@code '} or {@code "}).
   * @param intern if the string should be interned, normally only done for keys.
   * @return the index of the first byte after the string ends.
   */
  private int parseString(byte[] utf8, int i, int startChar, boolean intern) {
    throw new UnsupportedOperationException();
  }

  /**
   * Parse an unquoted text.
   *
   * <p>Unquoted texts can be found in keys of maps, or they are values of maps or arrays, or are found in the root. They basically can be anything, because JSON is not typed, so they can be {@code null}, {@code Boolean}, {@code Long}, {@code Double}, or a {@code String}. However, all of them should be trimmed from white spaces at the end.
   *
   * <p>An unquoted key is special case, it must end at a colon ({@code :}), while all other values will end at comma ({@code ,}), line-feed ({@code \n}), map close (<code>}</code>), or array close ({@code ]}). They are parsed until a valid end is found, and then trimmed reverse! The returned index will be positioned on the detected end, so that it can be read again.
   *
   * <p>The result of the parse will be stored in {@link #chars}, with {@link #chars_end} pointing to the first character that is not valid, and with {@link #chars_hash} being correct. Additionally, the parser will set {@link #potentialLong}, when the processed characters match a long value, or {@link #potentialDouble}, when the processed character match a JSON double. The result need to be interpreted within the context in which it was requested.
   *
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first valid string byte.
   * @param isKey if the string is a key, therefore must be followed by a colon ({@code :}), and should be interned.
   * @return the index of the first byte after the end character.
   */
  private int parseText(byte[] utf8, int i, boolean isKey) {
    throw new UnsupportedOperationException();
  }

  private int parseMap(byte[] utf8, int i) {
    throw new UnsupportedOperationException();
  }

  private int parseArray(byte[] utf8, int i) {
    throw new UnsupportedOperationException();
  }

  /**
   * Parse an arbitrary value into {@link #parsedValue}, possible values are:
   * <ul>
   *   <li>{@code null}
   *   <li>{@link Boolean}
   *   <li>{@link String}
   *   <li>{@link Long}
   *   <li>{@link Double}
   *   <li>{@link JsonMap}
   *   <li>{@link JsonArray}
   * </ul>
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first byte after the {@code startChar}.
   * @return the index of the first byte after the value ends or {@code -1} in error case.
   */
  private int parseValue(byte[] utf8, int i) {
    i = skipWhiteSpaces(utf8, i);
    final long result = decodeCodePoint(utf8, i);
    final int cp = resultCodePoint(result);
    i = resultNextIndex(result);
    switch (cp) {
      case '{': return parseMap(utf8, i);
      case '[': return parseArray(utf8, i);
      case '\'':
      case '"': return parseString(utf8, i, cp, false);
      default:
    }
    // Otherwise parse the text.
    i = parseText(utf8, i, false);
    if (i < 0) return i;

    final var chars = this.chars;
    final var chars_end = this.chars_end;

    // Empty text is an empty string.
    if (chars_end == 0) {
      parsedValue = StringUtil.EMPTY;
      return i;
    }

    // A single character text is simple.
    if (chars_end == 1) {
      final var c = chars[0];
      if (c >= '0' && c <= '9') {
        parsedValue = (long)(c - '0');
        return i;
      }
      // Note: For surrogate characters this will return null as value!
      parsedValue = StringUtil.ONE_CHAR[c];
      return i;
    }

    // can be `true` or `null`
    if (chars_end == 4) {
      final var c0 = chars[0];
      final var c1 = chars[1];
      final var c2 = chars[2];
      final var c3 = chars[3];
      if ((c0=='t'||c0=='T')
          && (c1=='r'||c1=='R')
          && (c2=='u'||c2=='U')
          && (c3=='e'||c3=='E')) {
        parsedValue = Boolean.TRUE;
        return i;
      }
      if ((c0=='n'||c0=='N')
          && (c1=='u'||c1=='U')
          && (c2=='l'||c2=='L')
          && (c3=='l'||c3=='L')) {
        parsedValue = null;
        return i;
      }
    }

    // can be `false`
    if (chars_end == 5) {
      final var c0 = chars[0];
      final var c1 = chars[1];
      final var c2 = chars[2];
      final var c3 = chars[3];
      final var c4 = chars[4];
      if ((c0=='f'||c0=='F')
          && (c1=='a'||c1=='A')
          && (c2=='l'||c2=='L')
          && (c3=='s'||c3=='S')
          && (c4=='e'||c4=='E')) {
        parsedValue = Boolean.FALSE;
        return i;
      }
    }

    // Now we are left only with number or string, test number first.
    try {
      if (potentialLong) {
        // TODO: Parse long, or throw NumberFormatException if failed.
        throw new UnsupportedOperationException();
      }
      if (potentialDouble) {
        parsedValue = parseDouble(chars, 0, chars_end);
        return i;
      }
    } catch (NumberFormatException ignored) {}
    // Obviously no long or double, so must be string.
    parsedValue = StringUtil.getOrNew(chars, 0, chars_end, chars_hash, isNFKCNormalized);
    return i;
  }

  /**
   * The index of the first byte that was not parsed.
   */
  public int end;

  /**
   * Parses the given UTF-8 encoded bytes that must contain a {@code JSON}.
   *
   * @param utf8 the JSON string as UTF-8 encoded bytes.
   * @return the parsed object or {@link JsonError}.
   */
  public @Nullable Object parse(byte[] utf8) {
    return parse(utf8, 0, false);
  }

  /**
   * Parses the given UTF-8 encoded bytes that must contain a {@code JSON}.
   *
   * @param utf8 the JSON string as UTF-8 encoded bytes.
   * @param i the first byte to read.
   * @return the parsed object or {@link JsonError}.
   */
  public @Nullable Object parse(byte[] utf8, int i) {
    return parse(utf8, i, false);
  }

  /**
   * Parses the given UTF-8 encoded bytes that must contain a {@code JSON}.
   *
   * @param utf8 the JSON string as UTF-8 encoded bytes.
   * @param i the first byte to read.
   * @param isNFKCNormalized if the characters are already in {@link Normalizer.Form#NFKC NFKC} form; otherwise detection needed.
   * @return the parsed object or {@link JsonError}.
   */
  public @Nullable Object parse(byte[] utf8, int i, boolean isNFKCNormalized) {
    if (utf8 == null) return null;
    charBuffer = ThreadLocalCharBuffer.instance.get();
    chars = charBuffer.get();
    chars_hash = 0;
    chars_end = 0;
    line = 0;
    column = 0;
    this.isNFKCNormalized = isNFKCNormalized;
    end = parseValue(utf8, i);
    return parsedValue;
  }
}