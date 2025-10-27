package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.Arrays;

import static ch.randelshofer.fastdoubleparser.JsonDoubleParser.parseDouble;
import static java.lang.Character.*;
import static naksha.base.Json.ensure_size;
import static naksha.base.NumberUtil.boxDouble;
import static naksha.base.NumberUtil.boxLong;
import static naksha.base.StringUtil.intern;
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
   * Create a new instance of JSON parser, this is not really deprecated, but not recommended. It is recommended to use the thread local instance, by doing {@code JsonParse.instance.get()}.
   * @see #instance
   */
  @Deprecated
  public JsonParser() {}

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
  private byte[] utf8_bytes;
  private char[] chars;
  private int chars_end;
  private int chars_hash;
  private boolean isNFKCNormalized;
  private int line;
  private int column;
  private @Nullable JsonError error;

  /** Expected end-of-file, so terminates the JSON parsing correctly. */
  public static final int EOF = -1;
  /** Erroneous end-of-file, so pre-mature terminates the JSON parsing. */
  public static final int UNEXPECTED_EOF = -2;
  /** Error in the UTF-8 encoding, for example missing bytes of multibyte code-point or invalid code-point value. */
  public static final int MALFORMED_UTF8 = -3;
  /** Erroneous JSON formatting. */
  public static final int MALFORMED_JSON = -4;
  /** Malformed hexadecimal character escape sequence (<code>&#92;xNN</code>). */
  public static final int MALFORMED_HEX_ESCAPE = -5;
  /** Malformed Unicode character escape sequence (<code>&#92;uNNNN</code> or <code>&#92;u{NNNNN}</code>). */
  public static final int MALFORMED_UNICODE_ESCAPE = -6;

  /**
   * Returns {@code -2}, and sets {@link #error} with the provided {@code index}, {@code line} and {@code column} as error source.
   *
   * <p>If an expected end-of-file happens, just return {@link #EOF}.
   * @param index the index within the {@code utf-8 bytes} that is erroneous.
   * @param line the line number in the source where the error happens.
   * @param column the column that is encoded wrong.
   * @return {@link #UNEXPECTED_EOF} <i>(-2)</i>.
   */
  private int error_eof(int index, int line, int column) {
    this.error = new JsonError("Unexpected end-of-file", utf8_bytes, index, line, column);
    return UNEXPECTED_EOF;
  }

  /**
   * Returns {@code -3}, and sets {@link #error} with the provided {@code index}, {@code line} and {@code column} as error source.
   * @param index the index within the {@code utf-8 bytes} that is erroneous.
   * @param line the line number in the source where the error happens.
   * @param column the column that is encoded wrong.
   * @return {@link #MALFORMED_UTF8} <i>(-3)</i>.
   */
  private int error_malformed_utf8(int index, int line, int column) {
    this.error = new JsonError("Malformed UTF-8 encoding", utf8_bytes, index, line, column);
    return MALFORMED_UTF8;
  }

  /**
   * Returns {@code -4}, and sets {@link #error} with the provided {@code index}, {@code line} and {@code column} as error source.
   * @param message the reason for the malformed JSON.
   * @param index the index within the {@code utf-8 bytes} that is erroneous.
   * @param line the line number in the source where the error happens.
   * @param column the column that is encoded wrong.
   * @return {@link #MALFORMED_JSON} <i>(-4)</i>.
   */
  private int error_malformed_json(@NotNull String message, int index, int line, int column) {
    this.error = new JsonError(message, utf8_bytes, index, line, column);
    return MALFORMED_JSON;
  }

  /**
   * Returns {@code -5}, and sets {@link #error} with the provided {@code index}, {@code line} and {@code column} as error source.
   * @param index the index within the {@code utf-8 bytes} that is erroneous.
   * @param line the line number in the source where the error happens.
   * @param column the column that is encoded wrong.
   * @return {@link #MALFORMED_HEX_ESCAPE} <i>(-5)</i>.
   */
  private int error_malformed_hex_escape(int index, int line, int column) {
    this.error = new JsonError("Malformed hexadecimal character escape sequence", utf8_bytes, index, line, column);
    return MALFORMED_HEX_ESCAPE;
  }

  /**
   * Returns {@code -6}, and sets {@link #error} with the provided {@code index}, {@code line} and {@code column} as error source.
   * @param index the index within the {@code utf-8 bytes} that is erroneous.
   * @param line the line number in the source where the error happens.
   * @param column the column that is encoded wrong.
   * @return {@link #MALFORMED_UNICODE_ESCAPE} <i>(-6)</i>.
   */
  private int error_malformed_unicode_escape(int index, int line, int column) {
    this.error = new JsonError("Malformed Unicode character escape sequence", utf8_bytes, index, line, column);
    return MALFORMED_UNICODE_ESCAPE;
  }

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
      int prev_cp = -1;
      int cp = -1;
      do {
        // We ignore carriage-return as previous characters, so that a backslash at the end of the line comment continues, even on windows.
        prev_cp = cp != '\r' ? cp : prev_cp;
        final var result = decodeCodePoint(utf8, i);
        i = resultGetNextIndex(result);
        if (i < 0) return EOF;
        cp = resultGetCodePoint(result);
        if (cp < 0) return error_malformed_utf8(i, line, column);
        if (cp == '\n') {
          line++;
          column = 0;
        } else {
          column++;
        }
      } while (prev_cp != '\\' && cp != '\n');
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
      do {
        prev_cp = cp;
        final var result = decodeCodePoint(utf8, i);
        i = resultGetNextIndex(result);
        if (i < 0) return EOF; // We simply allow the JSON to end with /*
        cp = resultGetCodePoint(result);
        if (cp < 0) return error_malformed_utf8(i, line, column);
        if (cp == '\n') {
          line++;
          column = 0;
        } else {
          column++;
        }
        // Allow escaping, so `prev_cp` becomes -1.
        // Therefore, for example `\*/` and `*\/` will work in block comments.
        if (cp == '\\') cp = -1;
      } while (prev_cp != '*' || cp != '/');
      return i;
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
    final int cp = resultGetCodePoint(result);
    if (cp < 0) return error_malformed_utf8(i, line, column);
    final int next_i = resultGetNextIndex(result);
    if (next_i < 0) return i; // this is the last code-point.
    if (cp == '*') return skipBlockComment(utf8, next_i);
    if (cp == '/') return skipLineComment(utf8, next_i);
    // This does not start a comment.
    return i;
  }

  private static boolean isWhitespace(int codePoint) {
    return codePoint < 32 || Character.isWhitespace(codePoint);
  }

  /**
   * Skip all white-spaces <i>(code-point below 32, space)</i>.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading.
   * @return the index of the first byte that is no white-space, being {@code i}, when no whitespace found.
   */
  private int skipWhiteSpaces(byte[] utf8, int i) {
    int line = this.line;
    int column = this.column;
    try {
      int cp;
      while (true) {
        final var result = decodeCodePoint(utf8, i);
        cp = resultGetCodePoint(result);
        assert cp >= 0;
        if (!isWhitespace(cp)) {
          // Return the current "i", because this (the next) code-point is basically the search none-whitespace.
          return i;
        }
        if (cp == '\n') {
          line++;
          column = 0;
        } else {
          column++;
        }
        i = resultGetNextIndex(result);
      }
    } finally {
      this.line = line;
      this.column = column;
    }
  }

  private Object parsedValue;
  private int potentialType;

  /**
   * Parse a quoted string, store the result in {@link #parsedValue}.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first byte after the {@code startChar}.
   * @param startChar the character the string started with ({@code '} or {@code "}).
   * @param intern if the string should be interned, normally only done for keys.
   * @param decodeDataUrl if true, then <a href="https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data">data-url's</a> that refer to {@code Uint8Array}, {@code Int8Array}, {@code Uint16Array}, {@code Int16Array}, {@code Uint32Array}, {@code Int32Array}, {@code BigUint64Array}, {@code BigInt64Array}, {@code Float32Array}, or {@code Float64Array} should be decoded automatically into the Java equivalent, so {@code byte[]}, {@code short[]}, {@code int[]}, {@code long[]}, {@code float[]}, or {@code double[]}.
   * @return the index of the first byte after the string ends.
   */
  private int parseString(final byte @NotNull [] utf8, int i, final int startChar, final boolean intern, final boolean decodeDataUrl) {
    final var HEX_TABLE = JsonParser.HEX_TABLE;
    char[] chars = this.chars;
    assert chars != null;
    int chars_hash = 0;
    int chars_length = 0;
    boolean escape = false;
    potentialType = TEXT;
    parsedValue = null;
    try {
      while (true) {
        final long result = decodeCodePoint(utf8, i);
        i = resultGetNextIndex(result);
        if (i < 0) return i;
        int cp = resultGetCodePoint(result);
        if (cp < 0) return error_malformed_utf8(i, line, column);
        if (!escape) {
          if (cp == '\\') {
            escape = true;
            continue;
          }
          if (cp == startChar) {
            return i;
          }
        } else { // escape sequences
          switch (cp) {
            case '0': cp = 0; break; // ASCII-0
            case 'b': cp = '\b'; break; // backspace
            case 'f': cp = '\f'; break; // form-feed
            case 'v': cp = 11; break; // vertical-tab
            case 'r': cp = '\r'; break; // carriage-return
            case 'n': cp = '\n'; break; // line-feed
            case 't': cp = '\t'; break; // tab
            case 'x': {
              // \xNN = Short hex escaped code-point
              long r = decodeCodePoint(utf8, i);
              int r_i = resultGetNextIndex(r);
              if (r_i < 0) return error_malformed_hex_escape(i, line, column);
              int r_cp = resultGetCodePoint(r);
              if (r_cp < 0) return error_malformed_utf8(i, line, column);
              final int h1 = r_cp >= HEX_TABLE.length ? -1 : HEX_TABLE[r_cp];
              if (h1 < 0) return error_malformed_hex_escape(i, line, column);

              r = decodeCodePoint(utf8, r_i);
              r_i = resultGetNextIndex(r);
              if (r_i < 0) return error_malformed_hex_escape(i, line, column);
              r_cp = resultGetCodePoint(r);
              if (r_cp < 0) return error_malformed_utf8(i, line, column);
              final int h2 = r_cp >= HEX_TABLE.length ? -1 : HEX_TABLE[r_cp];
              if (h2 < 0) return error_malformed_hex_escape(i, line, column);

              cp = (h1 << 4) + h2;
              i = r_i;
              break;
            }
            case 'u': {
              long r = decodeCodePoint(utf8, i);
              int r_i = resultGetNextIndex(r);
              if (r_i < 0) return error_malformed_hex_escape(i, line, column);
              int r_cp = resultGetCodePoint(r);
              if (r_cp < 0) return error_malformed_utf8(i, line, column);
              if (r_cp == '{') {
                // \\u{NNNNN} = Flexible hex escaped full code-point (N can be 1 to 5)
                cp = 0;
                int len = 0;
                do {
                  r = decodeCodePoint(utf8, r_i);
                  r_i = resultGetNextIndex(r);
                  if (r_i < 0) return error_malformed_hex_escape(i, line, column);
                  r_cp = resultGetCodePoint(r);
                  if (r_cp < 0) return error_malformed_utf8(i, line, column);
                  if (r_cp == '}') {
                    if (len == 0) return error_malformed_unicode_escape(i, line, column);
                    break;
                  }
                  int h = r_cp >= HEX_TABLE.length ? -1 : HEX_TABLE[r_cp];
                  if (h < 0) return error_malformed_unicode_escape(i, line, column);
                  cp = (cp << 4) + h;
                  if (++len == 6) return error_malformed_unicode_escape(i, line, column);
                } while (true);
              } else {
                // \\uNNNN = Single hex escaped BMP code-point
                final int h1 = r_cp >= HEX_TABLE.length ? -1 : HEX_TABLE[r_cp];
                if (h1 < 0) return error_malformed_unicode_escape(i, line, column);

                r = decodeCodePoint(utf8, r_i);
                r_i = resultGetNextIndex(r);
                if (r_i < 0) return error_malformed_hex_escape(i, line, column);
                r_cp = resultGetCodePoint(r);
                if (r_cp < 0) return error_malformed_utf8(i, line, column);
                final int h2 = r_cp >= HEX_TABLE.length ? -1 : HEX_TABLE[r_cp];
                if (h2 < 0) return error_malformed_unicode_escape(i, line, column);

                r = decodeCodePoint(utf8, r_i);
                r_i = resultGetNextIndex(r);
                if (r_i < 0) return error_malformed_hex_escape(i, line, column);
                r_cp = resultGetCodePoint(r);
                if (r_cp < 0) return error_malformed_utf8(i, line, column);
                final int h3 = r_cp >= HEX_TABLE.length ? -1 : HEX_TABLE[r_cp];
                if (h3 < 0) return error_malformed_unicode_escape(i, line, column);

                r = decodeCodePoint(utf8, r_i);
                r_i = resultGetNextIndex(r);
                if (r_i < 0) return error_malformed_hex_escape(i, line, column);
                r_cp = resultGetCodePoint(r);
                if (r_cp < 0) return error_malformed_utf8(i, line, column);
                final int h4 = r_cp >= HEX_TABLE.length ? -1 : HEX_TABLE[r_cp];
                if (h4 < 0) return error_malformed_unicode_escape(i, line, column);

                cp = (h1 << 12) + (h2 << 8) + (h3 << 4) + h4;
              }
              i = r_i;
              break;
            }
            // No special meaning, just use the current code point as is.
            case '\\':
            default:
          }
        }
        if (isBmpCodePoint(cp)) {
          chars = charBuffer.ensure(chars, chars_length);
          chars[chars_length] = (char) cp;
          chars_length += 1;
          chars_hash = chars_hash * 31 + cp;
        } else {
          chars = charBuffer.ensure(chars, chars_length + 1);
          final var hi = highSurrogate(cp);
          final var lo = lowSurrogate(cp);
          chars[chars_length] = hi;
          chars[chars_length + 1] = lo;
          chars_length += 2;
          chars_hash = ((chars_hash * 31) + hi) * 31 + lo;
        }
        escape = false;
      }
    } finally {
      this.chars = chars;
      this.chars_hash = chars_hash;
      this.chars_end = chars_length;
      // - Uint8Array
      // - Int8Array -> The shortest case with prefix being at least "data:Int8Array;base64,<data>", 23 chars.
      // - Uint16Array
      // - Int16Array
      // - Uint32Array
      // - Int32Array
      // - BigUint64Array
      // - BigInt64Array
      // - Float32Array
      // - Float64Array
      if (decodeDataUrl && chars_length > 22) { // The shortest data-url is: "data:Int8Array;base64,0", so 23 chars!
        // TODO: Decode `data:<type>;base64,<data>`, see https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data
      }
      if (chars_length == 0) {
        parsedValue = StringUtil.EMPTY;
      } else if (intern) {
        parsedValue = intern(chars, 0, chars_length, chars_hash, false, false);
      } else {
        parsedValue = newString(chars, 0, chars_length, chars_hash, false);
      }
    }
  }

  private static boolean[] VALUE_TERMINATOR = new boolean[128];
  static {
    VALUE_TERMINATOR[','] = true; // {a:foo,b:bar} -- {a:true,} -- [true,]
    VALUE_TERMINATOR['}'] = true; // {a:foo}
    VALUE_TERMINATOR[']'] = true; // [true]
    VALUE_TERMINATOR['\n'] = true;
    // {
    //   a:foo
    //   b:bar
    // }
  }

  // number = [ minus ] int [ frac ] [ exp ]
  //
  //  minus  = %x2D                        ; -
  //  int    = zero / ( digit1-9 *DIGIT )
  //  frac   = decimal-point 1*DIGIT
  //  exp    = e [ minus / plus ] 1*DIGIT
  //
  //  decimal-point = %x2E                 ; .
  //  digit1-9      = %x31-39              ; 1-9
  //  e             = %x65 / %x45          ; e E
  //  plus          = %x2B                 ; +
  //  zero          = %x30                 ; 0
  private static final int TYPE_START = 0;
  private static final int NUM_INT = 1;
  private static final int NUM_AFTER_DOT = 2; //
  private static final int NUM_AFTER_EXP_FIRST = 3;
  private static final int NUM_AFTER_EXP = 4;
  private static final int TEXT = 5;
  private static int[][] NUMBER_TABLE = new int[5][];
  static { // TYPE_START
    final int[] table = new int[128];
    Arrays.fill(table, TEXT);
    for (int i = '0'; i <= '9'; i++) table[i] = NUM_INT;
    table['-'] = NUM_INT; // 45
    NUMBER_TABLE[TYPE_START] = table;
  }
  static { // NUM_INT
    final int[] table = new int[128];
    Arrays.fill(table, TEXT);
    for (int i = '0'; i <= '9'; i++) table[i] = NUM_INT;
    table['.'] = NUM_AFTER_DOT; // 46
    table['e'] = NUM_AFTER_EXP_FIRST; // 101
    table['E'] = NUM_AFTER_EXP_FIRST; // 69
    NUMBER_TABLE[NUM_INT] = table;
  }
  static { // NUM_AFTER_DOT
    final int[] table = new int[102];
    Arrays.fill(table, TEXT);
    for (int i = '0'; i <= '9'; i++) table[i] = NUM_AFTER_DOT;
    table['e'] = NUM_AFTER_EXP_FIRST; // 101
    table['E'] = NUM_AFTER_EXP_FIRST; // 69
    NUMBER_TABLE[NUM_AFTER_DOT] = table;
  }
  static { // NUM_AFTER_EXP_FIRST
    final int[] table = new int[58];
    Arrays.fill(table, TEXT);
    for (int i = '0'; i <= '9'; i++) table[i] = NUM_AFTER_EXP;
    table['+'] = NUM_AFTER_EXP; // 43
    table['-'] = NUM_AFTER_EXP; // 45
    NUMBER_TABLE[NUM_AFTER_EXP_FIRST] = table;
  }
  static { // NUM_AFTER_EXP
    final int[] table = new int[58];
    Arrays.fill(table, TEXT);
    for (int i = '0'; i <= '9'; i++) table[i] = NUM_AFTER_EXP;
    NUMBER_TABLE[NUM_AFTER_EXP] = table;
  }
  private static int[] HEX_TABLE = new int[128];
  static {
    Arrays.fill(HEX_TABLE, -1);
    for (int i = '0'; i <= '9'; i++) HEX_TABLE[i] = i - '0';
    HEX_TABLE['a'] = HEX_TABLE['A'] = 10;
    HEX_TABLE['b'] = HEX_TABLE['B'] = 11;
    HEX_TABLE['c'] = HEX_TABLE['C'] = 12;
    HEX_TABLE['d'] = HEX_TABLE['D'] = 13;
    HEX_TABLE['e'] = HEX_TABLE['E'] = 14;
    HEX_TABLE['f'] = HEX_TABLE['F'] = 15;
  }

  /**
   * Parse an unquoted text <i>(does not support <a href="https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data">data-url</a> decoding!)</i>.
   *
   * <p>Unquoted texts can be found in keys of maps, or they are values of maps or arrays, or are found in the root. They basically can be anything, because JSON is not typed, so they can be {@code null}, {@code Boolean}, {@code Long}, {@code Double}, or a {@code String}. However, all of them should be trimmed from white spaces at the end.
   *
   * <p>An unquoted key is special case, it must end at a colon ({@code :}), while all other values will end at comma ({@code ,}), line-feed ({@code \n}), map close (<code>}</code>), or array close ({@code ]}). They are parsed until a valid end is found, and then trimmed reverse! The returned index will be positioned on the detected end, so that it can be read again.
   *
   * <p>The result of the parse will be stored in {@link #chars}, with {@link #chars_end} pointing to the first character that is not valid, and with {@link #chars_hash} being correct. Additionally, the parser will set {@link #potentialType}. The result need to be interpreted within the context in which it was requested.
   *
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so first valid byte to read.
   * @param isKey if the string is a key, therefore must be followed by a colon ({@code :}), and should be interned.
   * @return the index of the first byte after the end character.
   */
  private int parseText(final byte @NotNull [] utf8, int i, final boolean isKey) {
    char[] chars = this.chars;
    assert chars != null;
    int chars_hash = 0;
    int chars_length = 0;
    boolean escape = false;
    int type = TYPE_START;
    try {
      while (true) {
        final long r = decodeCodePoint(utf8, i);
        final int r_i = resultGetNextIndex(r);
        if (r_i < 0) return r_i;
        final int r_cp = resultGetCodePoint(r);
        if (r_cp < 0) return error_malformed_utf8(i, line, column);

        if (!escape) {
          if (r_cp == '\\') {
            escape = true;
            i = r_i;
            continue;
          }
          if (r_cp == '/') {
            final int new_i = skipIfComment(utf8, r_i);
            if (new_i != r_i) {
              if (new_i < 0) return new_i;
              i = new_i;
              continue;
            }
          }
          if (isKey) {
            if (r_cp == ':') return i;
            if (r_cp == '\n') return error_malformed_json("Expected colon, but found line-break", i, line, column);
          } else if (r_cp == ',' // {a:foo,b:bar} -- {a:true,} -- [true,]
              || r_cp == '}' // {a:foo}
              || r_cp == ']' // // [true]
              || r_cp == '\n' // {
            //   a:foo
            //   b:bar
            // }
          ) {
            return i;
          }
        }
        if (isBmpCodePoint(r_cp)) {
          chars = charBuffer.ensure(chars, chars_length);
          chars[chars_length] = (char) r_cp;
          chars_length += 1;
          chars_hash = chars_hash * 31 + r_cp;
        } else {
          chars = charBuffer.ensure(chars, chars_length + 1);
          final var hi = highSurrogate(r_cp);
          final var lo = lowSurrogate(r_cp);
          chars[chars_length] = hi;
          chars[chars_length + 1] = lo;
          chars_length += 2;
          chars_hash = ((chars_hash * 31) + hi) * 31 + lo;
          // Every extended code-point breaks the number assumption.
          type = TEXT;
        }
        escape = false;
        i = r_i;
      }
    } finally {
      // Trim leading white spaces.
      boolean invalidate_hash = false;
      int pos = 0;
      while (pos < chars_length && isWhitespace(chars[pos])) pos++;
      if (pos > 0) {
        invalidate_hash = true;
        final int new_length = chars_length - pos;
        System.arraycopy(chars, pos, chars, 0, new_length);
        chars_length = new_length;
      }
      // Trim trailing white spaces.
      final int last_pos = chars_length - 1;
      pos = last_pos;
      while (pos > 0 && isWhitespace(chars[pos])) pos--;
      if (pos != last_pos) {
        chars_length = pos + 1;
        invalidate_hash = true;
      }

      // Detect long and boolean types by chaining a bunch of tables.
      final int[][] NUMBER_TABLE = JsonParser.NUMBER_TABLE;
      pos = 0;
      while (type != TEXT && pos < chars_length) {
        final char c = chars[pos++];
        final int[] table = NUMBER_TABLE[type];
        if (c < table.length) {
          type = table[c];
        } else {
          type = TEXT;
        }
      }
      if (type == TEXT && invalidate_hash) {
        // We need to calculate hash again.
        chars_hash = 0;
        for (int j = 0; j < chars_length; j++) {
          chars_hash = (chars_hash * 31) + chars[j];
        }
      }
      this.potentialType = type;
      this.chars = chars;
      this.chars_hash = chars_hash;
      this.chars_end = chars_length;
    }
  }

  /// Allocate a new object[] at stack.
  private @Nullable Object @NotNull [] allocateAtStack() {
    final int stack_i = stack_end;
    Object[] data = stack[stack_i];
    if (data == null) {
      data = ensure_size(Json.EMPTY_ARRAY, 1, false);
      stack[stack_i] = data;
    }
    stack_end++;
    return data;
  }

  /// Release/free the top object[].
  private void releaseAtStack() {
    stack_end--;
  }

  private enum MapParserState {
    PARSE_KEY_OR_END,
    PARSE_COLON,
    PARSE_VALUE,
    PARSE_COMMA_OR_END;

    public @NotNull String toString(int CodePoint) {
      return toString();
    }
    public @NotNull String toString() {
      switch (this) {
        case PARSE_KEY_OR_END: return "Expected key or '}'";
        case PARSE_COLON: return "Expected ':'";
        case PARSE_VALUE: return "Expected value";
        case PARSE_COMMA_OR_END: return "Expected ',' or '}'";
      }
      return "Invalid internal parser state";
    }
  }

  /**
   * Called after a map is opened, so after the <code>{</code> character was hit. Parses the map and returns it in {@link #parsedValue} as {@link JsonMap}.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to read, so the first valid byte after the map open character <i>(<code>{</code>)</i>.
   * @return the index to continue reading at, so the first byte after the map close <i>(<code>}</code>)</i>
   */
  private int parseMap(byte[] utf8, int i) {
    @Nullable Object @NotNull [] data = allocateAtStack();
    int line = this.line;
    int column = this.column;
    try {
      // Note: We have four states, executed in order:
      // - PARSE_KEY_OR_END
      // - PARSE_COLON
      // - PARSE_VALUE_COMMA_OR_END
      // - PARSE_COMMA_OR_END
      @NotNull MapParserState state = MapParserState.PARSE_KEY_OR_END;
      int data_end = 0;
      while (true) {
        long r = decodeCodePoint(utf8, i);
        int next_i = resultGetNextIndex(r);
        if (next_i < 0) return error_eof(i, line, column);
        int cp = resultGetCodePoint(r);
        if (cp < 0) return error_malformed_utf8(i, line, column);
        column++;
        if (cp == '/') {
          final int new_i = skipIfComment(utf8, next_i);
          if (new_i != next_i) { // We skipped some comment.
            if (new_i < 0) return error_malformed_utf8(i, line, column);
            i = new_i;
            continue;
          }
          return error_malformed_json(state.toString(cp), i, line, column);
        }
        if (cp == ':') {
          if (state == MapParserState.PARSE_COLON) {
            state = MapParserState.PARSE_VALUE;
            i = next_i;
            continue;
          }
          return error_malformed_json(state.toString(cp), i, line, column);
        }
        if (cp == '}') {
          if (state == MapParserState.PARSE_COLON) {
            return error_malformed_json(state.toString(cp), i, line, column);
          }
          if (state == MapParserState.PARSE_VALUE) {
            // The happens for example for `{a:}`
            data = ensure_size(data, data_end+1, false);
            data[data_end++] = Json.UNDEFINED;
          }
          // Now we are in: `PARSE_KEY_OR_END` or `PARSE_COMMA_OR_END`
          // Therefore, we are done.
          final var content = Arrays.copyOf(data, data_end);
          parsedValue = new JsonMap(content);
          return next_i;
        }
        if (cp == ',') {
          if (state == MapParserState.PARSE_COLON) {
            return error_malformed_json(state.toString(cp), i, line, column);
          }
          if (state == MapParserState.PARSE_VALUE) {
            // The happens for example for `{a:,b:10}`, we treat the value as undefined, rather than fail.
            data = ensure_size(data, data_end+1, false);
            data[data_end++] = Json.UNDEFINED;
          }
          // We may be in PARSE_KEY_OR_END, e.g. `{a:1,,b:10}`, we ignore this as human syntax error, and continue to parse key.
          // We may be in PARSE_COMMA_OR_END, which is what is normal, we now continue to parse the key.
          state = MapParserState.PARSE_KEY_OR_END;
          i = next_i;
          continue;
        }
        if (cp == '\n') {
          line++; column = 0;
          if (state == MapParserState.PARSE_COMMA_OR_END) {
            // {
            //   a: 12\n <-- We want to support this
            //   b: 10
            // }
            // Therefore in this case only, we treat the LF as comma, and continue in the next line with parsing the key or close.
            state = MapParserState.PARSE_KEY_OR_END;
            i = next_i;
            continue;
          }
          // In other cases we treat it just like any other whitespace, that will be ignored.
        }
        if (isWhitespace(cp)) {
          i = skipWhiteSpaces(utf8, i);
          continue;
        }
        if (state == MapParserState.PARSE_COLON || state == MapParserState.PARSE_COMMA_OR_END) {
          // We expect key or value parsing at this point, no other state is valid!
          return error_malformed_json(state.toString(cp), i, line, column);
        }
        // Parse the value and continue at the next code-point after the value.
        if (state == MapParserState.PARSE_KEY_OR_END) {
          if (cp == '\'' || cp=='"') {
            i = parseString(utf8, next_i, cp, true, false);
          } else {
            i = parseText(utf8, i, true);
            if (i < -1) return i;
            parsedValue = intern(chars, 0, chars_end, chars_hash, isNFKCNormalized, false);
          }
          data = ensure_size(data, data_end+1, false);
          data[data_end] = parsedValue;
          data_end++;
          state = MapParserState.PARSE_COLON;
        } else {
          i = parseValue(utf8, i);
          if (i < -1) return i;
          data = ensure_size(data, data_end+1, false);
          data[data_end] = parsedValue;
          data_end++;
          state = MapParserState.PARSE_COMMA_OR_END;
        }
      }
    } finally {
      releaseAtStack();
      this.line = line;
      this.column = column;
    }
  }

  /**
   * Called after an array is opened, so after the <code>[</code> character was hit. Parses the array and returns it in {@link #parsedValue} as {@link JsonArray}.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so the first valid byte after the array open character <i>(<code>[</code>)</i>.
   * @return the index to continue reading at, so the first byte after the array close <i>(<code>]</code>)</i>
   */
  private int parseArray(byte[] utf8, int i) {
    @Nullable Object @NotNull [] data = allocateAtStack();
    try {
      int data_end = 0;
      boolean expect_comma = false;
      boolean lf = false;
      while (true) {
        long r = decodeCodePoint(utf8, i);
        int next_i = resultGetNextIndex(r);
        if (next_i < 0) return error_eof(i, line, column);
        int cp = resultGetCodePoint(r);
        if (cp < 0) return error_malformed_utf8(i, line, column);
        if (cp == '/') {
          final int new_i = skipIfComment(utf8, next_i);
          if (new_i != next_i) { // We skipped some comment.
            if (new_i < 0) return error_malformed_utf8(i, line, column);
            i = new_i;
            continue;
          }
          return error_malformed_json("Expected ',' or ']', but found '/'", i, line, column);
        }
        if (cp == ']') {
          final var content = Arrays.copyOf(data, data_end);
          parsedValue = new JsonArray(content);
          return next_i;
        }
        if (cp == '\n') {
          line++; column = 0;
          lf = true; // We allow:
          // [
          //   1
          //   2
          // ]
        }
        if (cp == ',') {
          if (!expect_comma) {
            // We encounter a comma without any value, this happens for example for `[1,,2]` or `[,1]`.
            data[data_end++] = Json.UNDEFINED;
          }
          expect_comma = false;
          i = next_i;
          continue;
        }
        if (isWhitespace(cp)) {
          i = skipWhiteSpaces(utf8, i);
          continue;
        }
        // No whitespace, so value (except we have a line-feed before!).
        if (expect_comma && !lf) {
          // TODO: Add some general code to add a code-point into a string, then add: ", but found '<cp>'"
          return error_malformed_json("Expected ',' or ']'", i, line, column);
        }
        // Parse value, starting at i, not r_i, because the current cp is part of the value!
        i = parseValue(utf8, i);
        if (i < 0) return i;
        data = ensure_size(data, data_end+1, false);
        data[data_end] = parsedValue;
        data_end++;
        expect_comma = true;
        lf = false;
      }
    } finally {
      releaseAtStack();
    }
  }

  private static final long[] MUL = new long[] {
      1L, // 0
      10L, // 1
      100L, // 2
      1_000L, // 3
      10_000L, // 4
      100_000L, // 5
      1_000_000L, // 6
      10_000_000L, // 7
      100_000_000L, // 8
      1_000_000_000L, // 9
      10_000_000_000L, // 10
      100_000_000_000L, // 11
      1_000_000_000_000L, // 12
      10_000_000_000_000L, // 13
      100_000_000_000_000L, // 14
      1_000_000_000_000_000L, // 15
      10_000_000_000_000_000L, // 16
      100_000_000_000_000_000L, // 17
      1_000_000_000_000_000_000L // 18
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
        if (value < 0) return boxLong(value);
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
    return boxLong(negative ? -value : value);
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
    int next_i;
    while (true) {
      i = skipWhiteSpaces(utf8, i);
      if (i < 0) return i;
      final long result = decodeCodePoint(utf8, i);
      next_i = resultGetNextIndex(result);
      if (next_i < 0) return i;
      final int cp = resultGetCodePoint(result);
      if (cp < 0) return error_malformed_utf8(i, line, column);
      switch (cp) {
        case '{': return parseMap(utf8, next_i);
        case '[': return parseArray(utf8, next_i);
        case '\'':
        case '"': return parseString(utf8, next_i, cp, false, true);
        case '/':
          final int after_comment = skipIfComment(utf8, next_i);
          if (after_comment < 0) return after_comment; // Error or EOF while parsing comment, in any case we're done.
          if (after_comment != next_i) { // A comment was skipped, content continues.
            i = after_comment;
            continue;
          }
          // No comment, we continue parsing as text.
        default:
      }
      // Parse the text to detect the type.
      i = parseText(utf8, i, false);
      if (i < EOF) return i;

      final var chars = this.chars;
      final var chars_end = this.chars_end;
      final var chars_hash = this.chars_hash;

      // We were asked to parse a value, but just found an empty text, this can happen for example in the following case:
      // [5,,6]
      // {a:,b:5}
      // We will treat this as `undefined`, JavaScript does it as well, therefore `{a:}` is not the same as `{a:null}`.
      if (chars_end == 0) {
        parsedValue = Json.UNDEFINED;
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
      final int type = potentialType;
      if (type == NUM_INT) {
        try {
          parsedValue = parseLong(chars, 0, chars_end);
          return i;
        } catch (NumberFormatException ignored) {
          // Ups, no long.
        }
      }
      if (type == NUM_AFTER_DOT || type == NUM_AFTER_EXP) {
        try {
          parsedValue = boxDouble(parseDouble(chars, 0, chars_end));
          return i;
        } catch (NumberFormatException ignored) {
          // Ups, no double.
        }
      }
      parsedValue = newString(chars, 0, chars_end, chars_hash, isNFKCNormalized);
      return i;
    }
  }

  /**
   * The index of the first byte that was not parsed, {@code -1} if EOF, other negative values indicate and error.
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
    utf8_bytes = utf8;
    charBuffer = ThreadLocalCharBuffer.instance.get();
    chars = charBuffer.get();
    error = null;
    line = 0;
    column = 0;
    stack_end = 0;
    this.isNFKCNormalized = isNFKCNormalized;
    end = parseValue(utf8, i);
    return error != null ? error : parsedValue;
  }
}