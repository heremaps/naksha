package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;

import static ch.randelshofer.fastdoubleparser.JsonDoubleParser.parseDouble;
import static java.lang.Character.isWhitespace;
import static naksha.base.StringUtil.newString;
import static naksha.base.UTF8.*;

/**
 * A fast low-level JSON parser. This class is very low-level, it actually parses a given UTF-8 encoded JSON string into one of the following types:
 * <ul>
 * <li>{@code null} -> {@code null}
 * <li>{@code Boolean} -> {@link Boolean}
 * <li>{@code Number} -> {@link Number}: Actually always results in {@link Long} or {@link Double}, they will be interned with some limits.
 * <li>{@code String} -> {@link String}: The returned strings are {@link NormalizerForm#NFKC NFKC} normalized, and if being keys in a map, they are interned. The interning guarantees that there will never be the same key twice in memory, which allows to compare keys by reference, basically you are fine to do {@code key1 == key2}. This makes maps much faster.
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
 * @implNote Internally, the JSON parser does encode {@code Array}'s and {@code Map}'s as {@code Object[]}. Technically, the parser will work optimal, when the JSON is not too deep, and has only small maps; huge maps with hundreds of key-value pairs are rather bad for the performance, while bigger arrays should perform okay.
 * @since 3.0
 */
public final class JsonParser {

  /**
   * The thread local parser instance to be used to reduce memory consumption.
   * @since 3.0
   */
  public static final ThreadLocal<@NotNull JsonParser> instance = ThreadLocal.withInitial(JsonParser::new);


  // Allocate 2 KiB for the stack (3 elements are used for the JVM header, size, and padding on 64-bit JVM).
  // We stick with this stack for the life-time of the parser.
  // The inner arrays are managed using `ArrayUtil`.
  private final @Nullable Object @NotNull[] @Nullable[] stack = new Object[256-3][];
  private int stack_end;
  private ThreadLocalCharBuffer charBuffer;
  private char[] chars;
  private int chars_end;
  private int chars_hash;
  private boolean isNFKCNormalized;
  private int line;
  private int column;

  /**
   * To be called ones a line comment has hit, so after reading {@code //}.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first byte after {@code //}.
   * @return the index of the first byte after the comment ends, so after <code>//\n</code>
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
   * @param i the index to start reading at, so first byte after <code>/*</code>.
   * @return the index of the first byte after the comment ends, so after <code>&ast;/</code>.
   */
  private int skipBlockComment(byte[] utf8, int i) {
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

  /**
   * To be called ones a line or block comment has hit, so after reading {@code /}. It will skip the comment, if there is any, otherwise it will just return the same i.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first byte after {@code /}.
   * @return the index of the first byte after the comment ends or the given {@code i}, if this was no comment.
   */
  private int skipIfComment(byte[] utf8, int i) {
    final var result = decodeCodePoint(utf8, i);
    final int cp = resultCodePoint(result);
    final int next_i = resultNextIndex(result);
    if (cp == '*') return skipBlockComment(utf8, next_i);
    if (cp == '/') return skipLineComment(utf8, next_i);
    return i;
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
   * Skip all white-spaces, including {@code ,}, {@code ;}, {@code :}, line-feed <code>\n</code>, carriage-return <code>\r</code> and others.
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

  private static final long[] MUL = new long[] {
      1_000_000_000_000_000_000L, // 18
      100_000_000_000_000_000L, // 17
      10_000_000_000_000_000L, // 16
      1_000_000_000_000_000L, // 15
      100_000_000_000_000L, // 14
      10_000_000_000_000L, // 13
      1_000_000_000_000L, // 12
      100_000_000_000L, // 11
      10_000_000_000L, // 10
      1_000_000_000L, // 9
      100_000_000L, // 8
      10_000_000L, // 7
      1_000_000L, // 6
      100_000L, // 5
      10_000L, // 4
      1_000L, // 3
      100L, // 2
      10L, // 1
      1L // 0
  };
  private static long v(char @NotNull[] chars, int offset, int pos, int length) {
    assert offset >= 0 && offset+pos < chars.length;
    assert pos >= 0 && pos <= 18;
    assert length >= 1 && length <= 19 && length > pos;

    final char digit = chars[offset+pos];
    assert digit >= '0' && digit <= '9';
    final int i = length - pos - 1;
    // assert i >= 0 && i <= 18 && i <= MUL.length;
    return (digit-'0') * MUL[i];
  }
  private @NotNull Long parseLong(char[] chars, int offset, int length) {
    //
    // Note: We're only called, when the chars are only digits, except for the first, which may be a minus!
    //
    //  2           1
    // 109 876 543 210 987 654 3210
    //  -9,223,372,036,854,775,808
    if (length <= 0 || length > 20) throw new NumberFormatException();
    //assert length >= 1 && length <= 20;
    final boolean negative;
    if (chars[offset] == '-') {
      negative = true;
      ++offset;
      // We basically have just `-`
      if (--length == 0) throw new NumberFormatException();
    } else {
      negative = false;
      if (length == 20) throw new NumberFormatException();
    }

    // TODO: We can use vector instructions for this, when we just get FFW to upgrade to latest JVM 25.
    // Compiler knows that we're in that range, so he will create a jump-table!
    //assert length >= 1 && length <= 19;
    final long value;
    switch (length) {
      case 19: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length)
                     + v(chars, offset, 11, length)
                     + v(chars, offset, 12, length)
                     + v(chars, offset, 13, length)
                     + v(chars, offset, 14, length)
                     + v(chars, offset, 15, length)
                     + v(chars, offset, 16, length)
                     + v(chars, offset, 17, length)
                     + v(chars, offset, 18, length);
        // This happens only when -9,223,372,036,854,775,808 is parsed,
        // because 9_223_372_036_854_775_800L + 8L = -9,223,372,036,854,775,808
        if (value < 0) return value;
      break;
      case 18: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length)
                     + v(chars, offset, 11, length)
                     + v(chars, offset, 12, length)
                     + v(chars, offset, 13, length)
                     + v(chars, offset, 14, length)
                     + v(chars, offset, 15, length)
                     + v(chars, offset, 16, length)
                     + v(chars, offset, 17, length);
      break;
      case 17: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length)
                     + v(chars, offset, 11, length)
                     + v(chars, offset, 12, length)
                     + v(chars, offset, 13, length)
                     + v(chars, offset, 14, length)
                     + v(chars, offset, 15, length)
                     + v(chars, offset, 16, length);
      break;
      case 16: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length)
                     + v(chars, offset, 11, length)
                     + v(chars, offset, 12, length)
                     + v(chars, offset, 13, length)
                     + v(chars, offset, 14, length)
                     + v(chars, offset, 15, length);
      break;
      case 15: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length)
                     + v(chars, offset, 11, length)
                     + v(chars, offset, 12, length)
                     + v(chars, offset, 13, length)
                     + v(chars, offset, 14, length);
      break;
      case 14: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length)
                     + v(chars, offset, 11, length)
                     + v(chars, offset, 12, length)
                     + v(chars, offset, 13, length);
      break;
      case 13: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length)
                     + v(chars, offset, 11, length)
                     + v(chars, offset, 12, length);
      break;
      case 12: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length)
                     + v(chars, offset, 11, length);
      break;
      case 11: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length)
                     + v(chars, offset, 10, length);
      break;
      case 10: value = v(chars, offset, 0, length)
                     + v(chars, offset, 1, length)
                     + v(chars, offset, 2, length)
                     + v(chars, offset, 3, length)
                     + v(chars, offset, 4, length)
                     + v(chars, offset, 5, length)
                     + v(chars, offset, 6, length)
                     + v(chars, offset, 7, length)
                     + v(chars, offset, 8, length)
                     + v(chars, offset, 9, length);
      break;
      case 9: value = v(chars, offset, 0, length)
                    + v(chars, offset, 1, length)
                    + v(chars, offset, 2, length)
                    + v(chars, offset, 3, length)
                    + v(chars, offset, 4, length)
                    + v(chars, offset, 5, length)
                    + v(chars, offset, 6, length)
                    + v(chars, offset, 7, length)
                    + v(chars, offset, 8, length);
      break;
      case 8: value = v(chars, offset, 0, length)
                    + v(chars, offset, 1, length)
                    + v(chars, offset, 2, length)
                    + v(chars, offset, 3, length)
                    + v(chars, offset, 4, length)
                    + v(chars, offset, 5, length)
                    + v(chars, offset, 6, length)
                    + v(chars, offset, 7, length);
      break;
      case 7: value = v(chars, offset, 0, length)
                    + v(chars, offset, 1, length)
                    + v(chars, offset, 2, length)
                    + v(chars, offset, 3, length)
                    + v(chars, offset, 4, length)
                    + v(chars, offset, 5, length)
                    + v(chars, offset, 6, length);
      break;
      case 6: value = v(chars, offset, 0, length)
                    + v(chars, offset, 1, length)
                    + v(chars, offset, 2, length)
                    + v(chars, offset, 3, length)
                    + v(chars, offset, 4, length)
                    + v(chars, offset, 5, length);
      break;
      case 5: value = v(chars, offset, 0, length)
                    + v(chars, offset, 1, length)
                    + v(chars, offset, 2, length)
                    + v(chars, offset, 3, length)
                    + v(chars, offset, 4, length);
      break;
      case 4: value = v(chars, offset, 0, length)
                    + v(chars, offset, 1, length)
                    + v(chars, offset, 2, length)
                    + v(chars, offset, 3, length);
      break;
      case 3: value = v(chars, offset, 0, length)
                    + v(chars, offset, 1, length)
                    + v(chars, offset, 2, length);
      break;
      case 2: value = v(chars, offset, 0, length)
                    + v(chars, offset, 1, length);
      break;
      case 1: value = v(chars, offset, 0, length);
      break;
      default:
        throw new NumberFormatException();
    }
    return negative ? -value : value;
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
    while (true) {
      i = skipWhiteSpaces(utf8, i);
      if (i < 0) return i;
      final long result = decodeCodePoint(utf8, i);
      final int cp = resultCodePoint(result);
      i = resultNextIndex(result);
      if (i < 0) return i;
      switch (cp) {
        case '/': {
          final int new_i = skipIfComment(utf8, i);
          if (new_i != i) {
            i = new_i;
            continue;
          }
          break;
        }
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
          parsedValue = (long) (c - '0');
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
        if ((c0 == 't' || c0 == 'T')
            && (c1 == 'r' || c1 == 'R')
            && (c2 == 'u' || c2 == 'U')
            && (c3 == 'e' || c3 == 'E')) {
          parsedValue = Boolean.TRUE;
          return i;
        }
        if ((c0 == 'n' || c0 == 'N')
            && (c1 == 'u' || c1 == 'U')
            && (c2 == 'l' || c2 == 'L')
            && (c3 == 'l' || c3 == 'L')) {
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
        if ((c0 == 'f' || c0 == 'F')
            && (c1 == 'a' || c1 == 'A')
            && (c2 == 'l' || c2 == 'L')
            && (c3 == 's' || c3 == 'S')
            && (c4 == 'e' || c4 == 'E')) {
          parsedValue = Boolean.FALSE;
          return i;
        }
      }

      // Now we are left only with number or string, test number first.
      try {
        if (potentialLong) {
          parsedValue = parseLong(chars, 0, chars_end);
          return i;
        }
        if (potentialDouble) {
          parsedValue = parseDouble(chars, 0, chars_end);
          return i;
        }
      } catch (NumberFormatException ignored) {
      }
      // Obviously no long or double, so must be string.
      parsedValue = newString(chars, 0, chars_end, chars_hash, isNFKCNormalized);
      return i;
    }
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
    stack_end = 0;
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