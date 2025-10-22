package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.Arrays;

import static ch.randelshofer.fastdoubleparser.JsonDoubleParser.parseDouble;
import static java.lang.Character.*;
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
        i = resultNextIndex(result);
        if (i < 0) return EOF;
        cp = resultCodePoint(result);
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
        i = resultNextIndex(result);
        if (i < 0) return EOF; // We simply allow the JSON to end with /*
        cp = resultCodePoint(result);
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
    final int cp = resultCodePoint(result);
    if (cp < 0) return error_malformed_utf8(i, line, column);
    final int next_i = resultNextIndex(result);
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
   * @return the index of the first byte that is no white-space.
   */
  private int skipWhiteSpaces(byte[] utf8, int i) {
    int line = this.line;
    int column = this.column;
    try {
      int cp;
      while (true) {
        final var result = decodeCodePoint(utf8, i);
        cp = resultCodePoint(result);
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
        i = resultNextIndex(result);
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
  private int parseString(byte[] utf8, int i, int startChar, boolean intern, boolean decodeDataUrl) {
    throw new UnsupportedOperationException();
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
  private int parseText(byte[] utf8, int i, boolean isKey) {
    char[] chars = this.chars;
    assert chars != null;
    int chars_hash = 0;
    int chars_length = 0;
    boolean escape = false;
    int type = TYPE_START;
    try {
      while (true) {
        final long result = decodeCodePoint(utf8, i);
        i = resultNextIndex(result);
        if (i < 0) return i;
        final int cp = resultCodePoint(result);
        if (cp < 0) return error_malformed_utf8(i, line, column);
        if (!escape) {
          if (cp == '\\') {
            escape = true;
            continue;
          }
          if (cp == '/') {
            final int next_i = skipIfComment(utf8, i);
            if (next_i != i) {
              if (next_i < 0) return next_i;
              i = next_i;
              continue;
            }
          }
          if (isKey) {
            if (cp == ':') return i;
            if (cp == '\n') return error_malformed_json("Expected colon, but found line-break", i, line, column);
          } else if (cp == ',' // {a:foo,b:bar} -- {a:true,} -- [true,]
              || cp == '}' // {a:foo}
              || cp == ']' // // [true]
              || cp == '\n' // {
            //   a:foo
            //   b:bar
            // }
          ) {
            return i;
          }
        }
        if (isBmpCodePoint(cp)) {
          chars = charBuffer.ensure(chars, chars_length);
          chars[chars_length] = (char) cp;
          chars_length += 1;
        } else {
          chars = charBuffer.ensure(chars, chars_length + 1);
          chars[chars_length] = highSurrogate(cp);
          chars[chars_length + 1] = lowSurrogate(cp);
          chars_length += 2;
          // Every extended code-point breaks the number assumption.
          type = TEXT;
        }
        escape = false;
      }
    } finally {
      // Trim leading white spaces.
      int pos = 0;
      while (pos < chars_length && isWhitespace(chars[pos])) pos++;
      if (pos > 0) {
        final int new_length = chars_length - pos;
        System.arraycopy(chars, pos, chars, 0, new_length);
        chars_length = new_length;
      }
      // Trim trailing white spaces.
      pos = chars_length - 1;
      while (pos > 0 && isWhitespace(chars[pos])) pos--;
      chars_length = pos + 1;

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
      this.potentialType = type;
      this.chars = chars;
      this.chars_hash = chars_hash;
      this.chars_end = chars_length;
    }
  }

  /**
   * Called after a map is opened, so after the <code>{</code> character was hit. Parses the map and returns it in {@link #parsedValue} as {@link JsonMap}.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so the first valid byte after the map open character <i>(<code>{</code>)</i>.
   * @return the index to continue reading at, so the first byte after the map close <i>(<code>}</code>)</i>
   */
  private int parseMap(byte[] utf8, int i) {
    // TODO: For now, wrap JsonMap into JvmMap!
    throw new UnsupportedOperationException();
  }

  /**
   * Called after an array is opened, so after the <code>[</code> character was hit. Parses the array and returns it in {@link #parsedValue} as {@link JsonArray}.
   * @param utf8 the UTF-8 bytes.
   * @param i the index to start reading at, so the first valid byte after the array open character <i>(<code>[</code>)</i>.
   * @return the index to continue reading at, so the first byte after the array close <i>(<code>]</code>)</i>
   */
  private int parseArray(byte[] utf8, int i) {
    // TODO: For now, wrap JsonArray into JvmList!
    throw new UnsupportedOperationException();
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
    int next_i;
    while (true) {
      i = skipWhiteSpaces(utf8, i);
      if (i < 0) return i;
      final long result = decodeCodePoint(utf8, i);
      next_i = resultNextIndex(result);
      if (next_i < 0) return i;
      final int cp = resultCodePoint(result);
      if (cp < 0) return error_malformed_utf8(i, line, column);
      switch (cp) {
        case '{': return parseMap(utf8, i);
        case '[': return parseArray(utf8, i);
        case '\'':
        case '"': return parseString(utf8, i, cp, false, true);
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
          parsedValue = parseDouble(chars, 0, chars_end);
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