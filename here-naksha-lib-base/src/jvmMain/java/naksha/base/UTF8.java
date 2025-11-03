package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import static naksha.base.JvmUtil.*;

/** A library that helps in processing of UTF-8 strings, optimized for performance. */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class UTF8 {

  /** The UTF-8 charset. */
  public static final Charset charset = StandardCharsets.UTF_8;

  /** The ISO-8859-1 charset. */
  public static final Charset iso8859_1 = StandardCharsets.ISO_8859_1;

  /** The minimal allowed unicode value. */
  public static final int UNICODE_MIN_VALUE = 0;

  /** The maximal allowed unicode value (2^21-1). */
  public static final int UNICODE_MAX_VALUE = 2097151;

  /** A bitmask to unmask valid bits, so that all other bits need to be zero, for valid code-points. */
  public static final int UNICODE_MASK = ~2097151;

  /**
   * Tests the given code-point for validity.
   * @param codePoint the code-point to test.
   * @return the given code-point.
   * @throws InvalidCodePoint if the given code-point is invalid.
   */
  public static int verifyCodePoint(int codePoint) {
    if ((codePoint & UNICODE_MASK) != 0) {
      throw new InvalidCodePoint(codePoint);
    }
    return codePoint;
  }

  /**
   * Returns the amount of byte the supplied code point (UNICODE character) will consume in UTF-8
   * encoding.
   *
   * @param c the code point.
   * @return the amount of byte the supplied code point will consume in UTF-8 encoding or zero if
   *     the supplied code is no valid UNICODE code point.
   */
  public static int bytesOfCodePoint(int c) {
    if (c < 0) {
      return 0;
    }
    if (c < 128) {
      return 1;
    }
    if (c < 2048) {
      return 2;
    }
    if (c < 65536) {
      return 3;
    }
    if (c < 2097152) {
      return 4;
    }
    return 0;
  }

  /**
   * Returns true if the given UTF-8 byte is a code point.
   *
   * @param b the byte to test.
   * @return true if this byte is a single UTF-8 code point; false if it is part of a multi-byte
   *     sequence.
   */
  public static boolean isCodePoint(byte b) {
    return (b & 128) == 0;
  }

  /**
   * Returns true if the given UTF-8 byte is a lead-in byte of a multi-byte sequence.
   *
   * @param b the byte to test.
   * @return true if this byte is a lead-in byte of a UTF8 multi-byte sequence; false otherwise.
   */
  public static boolean isLeadIn(byte b) {
    // 0xC0 = 1100 000
    // If two high bits are set, this is a lead-in byte.
    return (b & 0xC0) == 0xC0;
  }

  /**
   * Returns true if the given UTF-8 byte is no sequence byte of a multibyte sequence.
   *
   * @param b the byte to test.
   * @return true if the given UTF-8 byte is no sequence byte of a multibyte sequence; false otherwise.
   */
  public static boolean isNoSequenceByte(byte b) {
    // We know that all sequence bytes have the upper two bit set to 10, but no other byte of a multibyte sequence does have that.
    // The lead-in byte does have at least an 11 in the first two bit, if it is no multibyte sequence the top bit is zero (0x).
    // 0xC0 = 1100 0000
    // 0x80 = 1000 0000
    return (b & 0xC0) != 0x80;
  }

  /**
   * Returns the amount of bytes in which the code-point (UNICODE character) with the provided lead-in byte (for example first byte read form a stream) should be encoded.
   *
   * @param leadIn the lead-in byte.
   * @return the amount of bytes that must be combined to decode the code-point, a value between 1 and 4.
   * @throws InvalidEncoding if the given lead-in byte is invalid.
   */
  public static int bytesOfCodePointByLeadingByte(final byte leadIn) {
    // Invert the register, then count the leading zeros, two CPU instruction.
    final int leadBits = Integer.numberOfLeadingZeros(~leadIn);
    // JIT should create a jump table as the possible value range is limited to 0 to 32.
    // In addition, the order of execution does not matter, therefore,
    // the following code allows the CPU to reorder, execute in parallel,
    // and is beneficial for the branch predictor.
    switch (leadBits) {
      case 4: // 11110_xxx 10_xxxxxx 10_xxxxxx 10_xxxxxx
      case 3: // 1110_xxxx 10_xxxxxx 10_xxxxxx
      case 2: // 110_xxxxx 10_xxxxxx
        return leadBits;
      case 0: // 0_xxxxxxx
        return 1;
      default: // 110_xxxxx
        // Malformed UTF-8 so very likely no UTF-8 or broken encoding.
        throw new InvalidEncoding(leadIn);
    }
  }

  /**
   * Counts the amount of byte the given character sequence will consume in UTF-8 encoding.
   *
   * @param chars the character sequence that should be converted to UTF-8 encoding.
   * @return the amount of byte that would be necessary to encode the given string in UTF-8.
   */
  public static int bytesOfString(final CharSequence chars) {
    int length = 0;
    for (int i = 0, len = chars.length(); i < len; i++) {
      length += bytesOfCodePoint(chars.charAt(i));
    }
    return length;
  }

  /**
   * Encode the supplied code point into UTF-8 and write the resulting bytes to the output stream.
   *
   * @param out the output stream to which to write the created bytes.
   * @param c the code-point to write.
   * @throws InvalidCodePoint if the provided code-point is illegal.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws IOException if any error at the output stream happened.
   */
  public static void encodeCodePoint(final OutputStream out, final int c) throws IOException {
    verifyCodePoint(c);
    // 0xxxxxxx (7 bit)
    if (c < 128) {
      out.write((byte) c);
    } else
    // 110xxxxx 10xxxxxx (11 bit)
    if (c < 2048) {
      out.write((byte) (((c >> 6) & 31) | 192));
      out.write((byte) (((c) & 63) | 128));
    } else
    // 1110xxxx 10xxxxxx 10xxxxxx (16 bit)
    if (c < 65536) {
      out.write((byte) (((c >> 12) & 15) | 224));
      out.write((byte) (((c >> 6) & 63) | 128));
      out.write((byte) (((c) & 63) | 128));
    } else
    // 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx (21 bit)
    {
      out.write((byte) (((c >> 18) & 7) | 240));
      out.write((byte) (((c >> 12) & 63) | 128));
      out.write((byte) (((c >> 6) & 63) | 128));
      out.write((byte) (((c) & 63) | 128));
    }
  }

  /**
   * Encodes a single byte into a UNICODE code point. This is helpful in combination with {@link
   * #bytesOfCodePointByLeadingByte(byte)}.
   *
   * @param lead the lead-in byte.
   * @return the UNICODE code point.
   * @throws InvalidEncoding if the given byte is no lead-in byte.
   */
  public static int encode(byte lead) {
    if (lead < 0) {
      throw new InvalidEncoding(0, "Illegal lead-in byte");
    }
    return lead & 0b1111_1111;
  }

  /**
   * Encodes the given bytes into a UNICODE code point. This is helpful in combination with {@link
   * #bytesOfCodePointByLeadingByte(byte)}.
   *
   * @param lead the lead-in byte.
   * @param b1 the first sequence byte.
   * @return the UNICODE code point.
   * @throws InvalidEncoding if the given byte is no lead-in byte.
   */
  public static int encode(byte lead, byte b1) {
    if (bytesOfCodePointByLeadingByte(lead) != 2) {
      throw new InvalidEncoding(0, "Illegal lead-in byte");
    }
    if (isNoSequenceByte(b1)) {
      throw new InvalidEncoding(1, "Illegal sequence byte");
    }
    // lead-in: 110x_xxxx
    return ((lead & 0b0001_1111) << 6) | b1 & 0b0011_1111;
  }

  /**
   * Encodes the given bytes into a UNICODE code point. This is helpful in combination with {@link
   * #bytesOfCodePointByLeadingByte(byte)}.
   *
   * @param lead the lead-in byte.
   * @param b1 the first sequence byte.
   * @param b2 the second sequence byte.
   * @return the UNICODE code point.
   * @throws InvalidEncoding if the given byte is no lead-in byte.
   */
  public static int encode(byte lead, byte b1, byte b2) {
    if (bytesOfCodePointByLeadingByte(lead) != 3) {
      throw new InvalidEncoding(0, "Illegal lead-in byte");
    }
    if (isNoSequenceByte(b1)) {
      throw new InvalidEncoding(1, "Illegal sequence byte");
    }
    if (isNoSequenceByte(b2)) {
      throw new InvalidEncoding(2, "Illegal sequence byte");
    }
    // lead-in: 1110_xxxx
    return ((lead & 0b0000_1111) << 12) | ((b1 & 0b0011_1111) << 6) | (b2 & 0b0011_1111);
  }

  /**
   * Encodes the given bytes into a UNICODE code point. This is helpful in combination with {@link
   * #bytesOfCodePointByLeadingByte(byte)}.
   *
   * @param lead the lead-in byte.
   * @param b1 the first sequence byte.
   * @param b2 the second sequence byte.
   * @param b3 the third sequence byte.
   * @return the UNICODE code point.
   * @throws InvalidEncoding if the given byte is no lead-in byte.
   */
  public static int encode(byte lead, byte b1, byte b2, byte b3) {
    if (bytesOfCodePointByLeadingByte(lead) != 4) {
      throw new InvalidEncoding(0, "Illegal lead-in byte");
    }
    if (isNoSequenceByte(b1)) {
      throw new InvalidEncoding(1, "Illegal sequence byte");
    }
    if (isNoSequenceByte(b2)) {
      throw new InvalidEncoding(2, "Illegal sequence byte");
    }
    if (isNoSequenceByte(b3)) {
      throw new InvalidEncoding(3, "Illegal sequence byte");
    }
    // lead-in: 1111_0xxx
    return ((lead & 0b0000_0111) << 18)
        | ((b1 & 0b0011_1111) << 12)
        | ((b2 & 0b0011_1111) << 6)
        | (b3 & 0b0011_1111);
  }

  /**
   * Encodes the given bytes into a UNICODE code point. This is helpful in combination with {@link
   * #bytesOfCodePointByLeadingByte(byte)}.
   *
   * @param lead the lead-in byte.
   * @param b1 the first sequence byte.
   * @param b2 the second sequence byte.
   * @param b3 the third sequence byte.
   * @param b4 the forth sequence byte.
   * @return the UNICODE code point.
   * @throws InvalidEncoding if the given byte is no lead-in byte.
   */
  public static int encode(byte lead, byte b1, byte b2, byte b3, byte b4) {
    if (bytesOfCodePointByLeadingByte(lead) != 5) {
      throw new InvalidEncoding(0, "Illegal lead-in byte");
    }
    if (isNoSequenceByte(b1)) {
      throw new InvalidEncoding(1, "Illegal sequence byte");
    }
    if (isNoSequenceByte(b2)) {
      throw new InvalidEncoding(2, "Illegal sequence byte");
    }
    if (isNoSequenceByte(b3)) {
      throw new InvalidEncoding(3, "Illegal sequence byte");
    }
    if (isNoSequenceByte(b4)) {
      throw new InvalidEncoding(4, "Illegal sequence byte");
    }
    // lead-in: 1111_10xx
    return ((lead & 0b0000_0011) << 24)
        | ((b1 & 0b0011_1111) << 18)
        | ((b2 & 0b0011_1111) << 12)
        | ((b3 & 0b0011_1111) << 6)
        | (b4 & 0b0011_1111);
  }

  /**
   * Encode the supplied code point into UTF-8 and write the resulting bytes to the supplied output buffer.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param codePoint the code point to write.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws InvalidCodePoint if the provided code-point is illegal.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeCodePoint(final byte[] bytes, int index, int end, final int codePoint)
      throws InvalidCodePoint, NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    verifyCodePoint(codePoint);

    // 0xxxxxxx (7 bit)
    if (codePoint < 128) {
      if (index >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) codePoint;
      return index+1;
    }

    // 110xxxxx 10xxxxxx (11 bit)
    if (codePoint < 2048) {
      if (index + 1 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) (((codePoint >> 6) & 31) | 192);
      bytes[index+1] = (byte) (((codePoint) & 63) | 128);
      return index+2;
    }

    // 1110xxxx 10xxxxxx 10xxxxxx (16 bit)
    if (codePoint < 65536) {
      if (index + 2 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) (((codePoint >> 12) & 15) | 224);
      bytes[index+1] = (byte) (((codePoint >> 6) & 63) | 128);
      bytes[index+2] = (byte) (((codePoint) & 63) | 128);
      return index+3;
    }

    // 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx (21 bit)
    if (index + 3 >= end) {
      throw new Overflow(index);
    }
    bytes[index] = (byte) (((codePoint >> 18) & 7) | 240);
    bytes[index+1] = (byte) (((codePoint >> 12) & 63) | 128);
    bytes[index+2] = (byte) (((codePoint >> 6) & 63) | 128);
    bytes[index+3] = (byte) (((codePoint) & 63) | 128);
    return index+4;
  }

  /**
   * Encodes the given character into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws InvalidEncoding if the given character is a {@link Character#isSurrogate(char) surrogate} character.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeChar(final byte[] bytes, final int index, final int end, final char value)
      throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    if (Character.isSurrogate(value)) {
      throw new InvalidEncoding(index, "The given character is a surrogate character that can't be encoded standalone");
    }
    return encodeCodePoint(bytes, index, end, value);
  }

  /**
   * Encodes the given value into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeNumber(final byte[] bytes, final int index, final int end, final byte value)
      throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    return encodeNumber(bytes, index, end, (long)value);
  }

  /**
   * Encodes the given value into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeNumber(final byte[] bytes, final int index, final int end, final short value)
      throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    return encodeNumber(bytes, index, end, (long)value);
  }

  /**
   * Encodes the given value into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeNumber(final byte[] bytes, final int index, final int end, final int value)
      throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    return encodeNumber(bytes, index, end, (long)value);
  }

  /**
   * Encodes the given value into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeNumber(final byte[] bytes, int index, int end, long value)
      throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    // This is a special case, because we can't invert the minimal negative long (-9,223,372,036,854,775,808).
    if (value == Long.MIN_VALUE) {
      // It has 19 digits.
      if (index+19 >= end) {
        throw new Overflow(index);
      }
      // Not using `index++` allows the JIT compiler to use vector instructions,
      // and the CPU to execute in parallel and/or to re-order instructions!
      // -9,223,372,036,854,775,808
      bytes[index] = '-';
      bytes[index+1] = '9';

      bytes[index+2] = '2';
      bytes[index+3] = '2';
      bytes[index+4] = '3';

      bytes[index+5] = '3';
      bytes[index+6] = '7';
      bytes[index+7] = '2';

      bytes[index+8] = '0';
      bytes[index+9] = '3';
      bytes[index+10] = '6';

      bytes[index+11] = '8';
      bytes[index+12] = '5';
      bytes[index+13] = '4';

      bytes[index+14] = '7';
      bytes[index+15] = '7';
      bytes[index+16] = '5';

      bytes[index+17] = '8';
      bytes[index+18] = '0';
      bytes[index+19] = '8';
      return index + 20;
    }

    // For negative numbers, we write the minus and then invert the number into positive,
    // sharing code between negative and positive encoding.
    if (value < 0L) {
      if (index >= end) {
        throw new Overflow(index);
      }
      bytes[index++] = '-';
      value = -value;
    }

    // This is optimized code, it only looks ugly!
    // We only add branches to detect the length of the encoding, after this pure math!
    // This allows JIT compiler to compile each branch down to SIMD instructions,
    // and the CPU to optimize for parallel execution, and re-ordering, while is very deterministic for the branch predictor!
    if (value < 10L) {
      if (index >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) value);
      return index + 1;
    }

    if (value < 100L) {
      if (index+1 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 10L));
      bytes[index+1] = (byte) ('0' + (int) (value % 10L));
      return index + 2;
    }

    if (value < 1_000L) {
      if (index+2 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 100L));
      bytes[index+1] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) (value % 10L));
      return index + 3;
    }

    if (value < 10_000L) {
      if (index+3 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 1_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) (value % 10L));
      return index + 4;
    }

    if (value < 100_000L) {
      if (index+4 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 10_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) (value % 10L));
      return index + 5;
    }

    if (value < 1_000_000L) {
      if (index+5 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 100_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) (value % 10L));
      return index + 6;
    }

    if (value < 10_000_000L) {
      if (index+6 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 1_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) (value % 10L));
      return index + 7;
    }

    if (value < 100_000_000L) {
      if (index+7 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 10_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) (value % 10L));
      return index + 8;
    }

    if (value < 1_000_000_000L) {
      if (index+8 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 100_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) (value % 10L));
      return index + 9;
    }

    if (value < 10_000_000_000L) {
      if (index+9 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 1_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) (value % 10L));
      return index + 10;
    }

    if (value < 100_000_000_000L) {
      if (index+10 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 10_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+10] = (byte) ('0' + (int) (value % 10L));
      return index + 11;
    }

    if (value < 1_000_000_000_000L) {
      if (index+11 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 100_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/10_000_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+10] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+11] = (byte) ('0' + (int) (value % 10L));
      return index + 12;
    }

    if (value < 10_000_000_000_000L) {
      if (index+12 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 1_000_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/100_000_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/10_000_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+10] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+11] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+12] = (byte) ('0' + (int) (value % 10L));
      return index + 13;
    }

    if (value < 100_000_000_000_000L) {
      if (index+13 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 10_000_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/1_000_000_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/100_000_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/10_000_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+10] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+11] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+12] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+13] = (byte) ('0' + (int) (value % 10L));
      return index + 14;
    }

    if (value < 1_000_000_000_000_000L) {
      if (index+14 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 100_000_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/10_000_000_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/1_000_000_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/100_000_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/10_000_000_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+10] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+11] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+12] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+13] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+14] = (byte) ('0' + (int) (value % 10L));
      return index + 15;
    }

    if (value < 10_000_000_000_000_000L) {
      if (index+15 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 1_000_000_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/100_000_000_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/10_000_000_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/1_000_000_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/100_000_000_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/10_000_000_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+10] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+11] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+12] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+13] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+14] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+15] = (byte) ('0' + (int) (value % 10L));
      return index + 16;
    }

    if (value < 100_000_000_000_000_000L) {
      if (index+16 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 10_000_000_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/1_000_000_000_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/100_000_000_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/10_000_000_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/1_000_000_000_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/100_000_000_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/10_000_000_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+10] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+11] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+12] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+13] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+14] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+15] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+16] = (byte) ('0' + (int) (value % 10L));
      return index + 17;
    }

    if (value < 1_000_000_000_000_000_000L) {
      if (index+17 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = (byte) ('0' + (int) (value / 100_000_000_000_000_000L));
      bytes[index+1] = (byte) ('0' + (int) ((value/10_000_000_000_000_000L) % 10L));
      bytes[index+2] = (byte) ('0' + (int) ((value/1_000_000_000_000_000L) % 10L));
      bytes[index+3] = (byte) ('0' + (int) ((value/100_000_000_000_000L) % 10L));
      bytes[index+4] = (byte) ('0' + (int) ((value/10_000_000_000_000L) % 10L));
      bytes[index+5] = (byte) ('0' + (int) ((value/1_000_000_000_000L) % 10L));
      bytes[index+6] = (byte) ('0' + (int) ((value/100_000_000_000L) % 10L));
      bytes[index+7] = (byte) ('0' + (int) ((value/10_000_000_000L) % 10L));
      bytes[index+8] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
      bytes[index+9] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
      bytes[index+10] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
      bytes[index+11] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
      bytes[index+12] = (byte) ('0' + (int) ((value/100_000L) % 10L));
      bytes[index+13] = (byte) ('0' + (int) ((value/10_000L) % 10L));
      bytes[index+14] = (byte) ('0' + (int) ((value/1_000L) % 10L));
      bytes[index+15] = (byte) ('0' + (int) ((value/100L) % 10L));
      bytes[index+16] = (byte) ('0' + (int) ((value/10L) % 10L));
      bytes[index+17] = (byte) ('0' + (int) (value % 10L));
      return index + 18;
    }

    if (index+18 >= end) {
      throw new Overflow(index);
    }
    bytes[index] = (byte) ('0' + (int) (value / 1_000_000_000_000_000_000L));
    bytes[index+1] = (byte) ('0' + (int) ((value/100_000_000_000_000_000L) % 10L));
    bytes[index+2] = (byte) ('0' + (int) ((value/1_000_000_000_000_000L) % 10L));
    bytes[index+3] = (byte) ('0' + (int) ((value/1_000_000_000_000_000L) % 10L));
    bytes[index+4] = (byte) ('0' + (int) ((value/100_000_000_000_000L) % 10L));
    bytes[index+5] = (byte) ('0' + (int) ((value/10_000_000_000_000L) % 10L));
    bytes[index+6] = (byte) ('0' + (int) ((value/1_000_000_000_000L) % 10L));
    bytes[index+7] = (byte) ('0' + (int) ((value/100_000_000_000L) % 10L));
    bytes[index+8] = (byte) ('0' + (int) ((value/10_000_000_000L) % 10L));
    bytes[index+9] = (byte) ('0' + (int) ((value/1_000_000_000L) % 10L));
    bytes[index+10] = (byte) ('0' + (int) ((value/100_000_000L) % 10L));
    bytes[index+11] = (byte) ('0' + (int) ((value/10_000_000L) % 10L));
    bytes[index+12] = (byte) ('0' + (int) ((value/1_000_000L) % 10L));
    bytes[index+13] = (byte) ('0' + (int) ((value/100_000L) % 10L));
    bytes[index+14] = (byte) ('0' + (int) ((value/10_000L) % 10L));
    bytes[index+15] = (byte) ('0' + (int) ((value/1_000L) % 10L));
    bytes[index+16] = (byte) ('0' + (int) ((value/100L) % 10L));
    bytes[index+17] = (byte) ('0' + (int) ((value/10L) % 10L));
    bytes[index+18] = (byte) ('0' + (int) (value % 10L));
    return index + 19;
  }

  /**
   * Encodes the given value into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeNumber(final byte[] bytes, int index, int end, final float value)
      throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    return encodeNumber(bytes, index, end, (double)value);
  }

  /**
   * Encodes the given value into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws IllegalArgumentException if the given value is {@code NaN} or any form of {@code infinity}.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeNumber(final byte[] bytes, int index, int end, final double value)
      throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    // If the double is an integer in long range, we can encode a long, plus a simple `.0`!.
    final long longValue = (long) value; // single CPU instruction!
    final double truncatedValue = (double) longValue; // single CPU instruction!
    // This means, the value is an integer in long range, and we now have the real value in `longValue`!
    if (value == truncatedValue) {
      index = encodeNumber(bytes, index, end, longValue);
      if (index+1 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = '.';
      bytes[index+1] = '0';
      return index+2;
    }

    // TODO: optimize this!
    final String s = Double.toString(value);
    final int length = s.length();
    if (index+length >= end) {
      throw new Overflow(index);
    }
    for (int i = 0; i < length; i++) {
      // We know this is ASCII, so always below 127 !
      final byte c = (byte) s.charAt(i);
      bytes[index++] = c;
    }
    return index;
  }

  /**
   * Encodes the given value into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeBoolean(final byte[] bytes, int index, int end, final boolean value)
      throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow
  {
    if (end < 0) {
      end = bytes.length + end;
    }

    if (value) {
      if (index + 3 >= end) {
        throw new Overflow(index);
      }
      bytes[index] = 't';
      bytes[index+1] = 'r';
      bytes[index+2] = 'u';
      bytes[index+3] = 'e';
      return index+4;
    }

    if (index + 4 >= end) {
      throw new Overflow(index);
    }
    bytes[index] = 'f';
    bytes[index+1] = 'a';
    bytes[index+2] = 'l';
    bytes[index+3] = 's';
    bytes[index+4] = 'e';
    return index+5;
  }

  /**
   * Encodes the given value into UTF-8 encoded bytes in the bytes array.
   *
   * @param bytes the byte array into which the encoded bytes should be written.
   * @param index the index into the byte array where to write the first byte.
   * @param end the index of the first byte that must not be modified.
   * @param value the value to be writen <i>(null will be written as the string {@code null})</i>.
   * @return the index in the byte array where the next byte should be written or in other words, the index of the first byte that was not modified.
   * @throws NullPointerException if the provided bytes array is null.
   * @throws InvalidEncoding if the given string contains invalid code-points (should not happen).
   * @throws ArrayIndexOutOfBoundsException if the index is out of bounds or the code point is too large to encode it into the given array, so the array has too less space left available.
   * @throws Overflow if the code point would exceed the given end.
   */
  public static int encodeText(
      final byte @NotNull [] bytes,
      int index,
      final int end,
      final @Nullable CharSequence value
  ) throws NullPointerException, ArrayIndexOutOfBoundsException, Overflow {
    if (value == null) {
      if (index + 3 >= end) {
        throw new Overflow(index);
      }
      bytes[index++] = 'n';
      bytes[index++] = 'u';
      bytes[index++] = 'l';
      bytes[index++] = 'l';
      return index;
    }

    final int length = value.length();
    int i = 0;
    while (i < length) {
      final char c = value.charAt(i++);
      if (Character.isHighSurrogate(c)) {
        if (i >= length) {
          throw new InvalidEncoding(index, "Found high-surrogate, but string ends pre-mature without low-surrogate");
        }
        final var low = value.charAt(i++);
        final var codePoint = Character.toCodePoint(c, low);
        index = encodeCodePoint(bytes, index, end, codePoint);
      } else {
        index = encodeCodePoint(bytes, index, end, c);
      }
    }
    return index;
  }

  /**
   * Parse a zero terminated UTF8 encoded string from the provided binary reader. This means it
   * reads all UTF-8 characters until either a character with the character code 0 is found or until
   * the reader throws an BufferUnderflowException, because there are no more bytes to be read.
   * Please be aware that C strings are not binary safe, therefore it is not possible to convert a
   * Java string into a C string without any escaping.
   *
   * <p><b>Note</b>: This method uses a thread local character buffer and the global string cache of
   * the util class StringCache. The method returns a UCS-2 string, therefore characters above the
   * character code 65535 are not supported.
   *
   * @param in the input stream from which to read the characters.
   * @return the parsed string.
   * @throws InvalidCodePoint if the read code point is illegal, no valid UNICODE.
   * @throws InvalidEncoding if the UTF-8 encoding is broken.
   * @throws NullPointerException if the provided binary reader is null.
   * @throws Exception if the reading from the binary input stream failed.
   */
  public static @NotNull String parseCString(final @NotNull InputStream in) throws Exception {
    return parseCString(in, Integer.MAX_VALUE, false);
  }

  /**
   * Parse a zero terminated UTF-8 encoded {@code C} string from the provided input-stream. The length is used as the maximal amount of bytes to read, including the terminating zero byte.
   *
   * <p>If the argument {@code binarySafe} is set to true, then the length no longer the maximal length, but is expected to be an exact value, that means that the length including the zero terminating byte must be exactly the provided length, no more, no less. Therefore, in that case the provided {@code length} argument must be greater/equal {@code 1}.
   *
   * <p><b>Note</b>: This method uses the thread local character buffer, and the global string cache of the {@link StringUtil} class.
   *
   * @param in the input stream from which to read the characters.
   * @param length the length of the string, either as maximal or the absolute length, including the zero terminated byte.
   * @param binarySafe if this argument is true, the length is the absolute length; otherwise the maximal length.
   * @return the parsed string.
   * @throws InvalidCodePoint if the read code-point is illegal, no valid UNICODE.
   * @throws InvalidEncoding if the UTF-8 encoding is broken.
   * @throws NullPointerException if the provided binary reader is null.
   * @throws IOException if the reading from the binary input stream failed.
   */
  public static @NotNull String parseCString(
      final @NotNull InputStream in,
      final int length,
      final boolean binarySafe
  ) throws IOException {
    final ThreadLocalCharBuffer charBuffer = ThreadLocalCharBuffer.instance.get();
    char[] chars = charBuffer.get();
    int hash = 0;
    int i = 0;
    while (true) {
      int codePoint = in.read();

      // If this is no binary safe read, and we found a zero byte ...
      if (!binarySafe && codePoint <= 0) {
        // ... this is the end of the string (we do not add the terminating ASCII zero).
        break;
      }

      // If we've reached the maximal length ...
      if (i == length) {
        // ... the character at the current position must be a zero byte, otherwise this is an encoding error!
        if (codePoint != 0) {
          throw new InvalidEncoding(in, (byte) codePoint);
        }
        // We do not add the terminating ASCII zero.
        break;
      }

      // Decode the code-point from the input.
      switch (bytesOfCodePointByLeadingByte((byte) codePoint)) {
        case 1:
          // The codePoint contains already the valid character.
          break;
        case 2:
          codePoint = decodeCodePoint((byte) codePoint, (byte)in.read());
          break;
        case 3:
          codePoint = decodeCodePoint((byte) codePoint, (byte)in.read(), (byte)in.read());
          break;
        case 4:
          codePoint = decodeCodePoint((byte) codePoint, (byte)in.read(), (byte)in.read(), (byte)in.read());
          break;
        default:
          throw new InvalidEncoding(in, (byte) codePoint);
      }

      if (Character.isBmpCodePoint(codePoint)) {
        chars = charBuffer.ensure(chars, i);
        chars[i++] = (char) codePoint;
        hash = hash * 31 + codePoint;
      } else {
        chars = charBuffer.ensure(chars, i+1);
        final char hi = Character.highSurrogate(codePoint);
        chars[i] = hi;
        final char lo = Character.lowSurrogate(codePoint);
        chars[i+1] = lo;
        hash = (hash * 31 + hi) * 31 + lo;
        i += 2;
      }
    }
    String s = StringUtil.interned(chars, 0, i, hash);
    if (s == null) s = new String(chars, 0, i);
    if (Normalizer.isNormalized(s, Normalizer.Form.NFKC)) return s;
    return Normalizer.normalize(s, Normalizer.Form.NFKC);
  }

  /**
   * Decodes a code-point from the supplied buffer.
   *
   * <h2>Beware</h2>
   * It is the job of the caller to test for {@code EOF} before calling this method, please check that {@link ByteBuffer#remaining()} is greater or equal to {@code 1}, before invoking this method. If there is only one remaining byte, but a multibyte is required, the method will return {@code -1}, but it will as well do so, if it gets called with no remaining bytes in the byte-buffer.
   *
   * @param buffer the buffer from which to read bytes.
   * @return the decoded code point, {@code -1} when the UTF-8 is malformed or there are no more remaining bytes in the byte-buffer.
   */
  public static int decodeCodePoint(final @NotNull ByteBuffer buffer) {
    if (buffer.remaining() == 0) return -1;
    // Widen into 32-bit register, single CPU instruction.
    final int c = buffer.get();
    // Invert the register, then count the leading zeros, two CPU instruction.
    final int leadBits = Integer.numberOfLeadingZeros(~c);

    // JIT should create a jump table as the possible value range is limited to 0 to 32.
    // In addition, the order of execution does not matter, therefore,
    // the following code allows the CPU to reorder, execute in parallel,
    // and is beneficial for the branch predictor.
    switch (leadBits) {
      // 11110_xxx 10_xxxxxx 10_xxxxxx 10_xxxxxx
      case 4: {
        if (buffer.remaining() < 3) return -1;
        final int b2 = buffer.get();
        final int b3 = buffer.get();
        final int b4 = buffer.get();
        return ((c & 0b00000_111) << 18)
            + ((b2 & 0b00_111111) << 12)
            + ((b3 & 0b00_111111) << 6)
            + (b4 & 0b00_111111);
      }
      // 1110_xxxx 10_xxxxxx 10_xxxxxx
      case 3: {
        if (buffer.remaining() < 2) return -1;
        final int b2 = buffer.get();
        final int b3 = buffer.get();
        return ((c & 0b0000_1111) << 12)
            + ((b2 & 0b00_111111) << 6)
            + (b3 & 0b00_111111);
      }
      // 110_xxxxx 10_xxxxxx
      case 2: {
        if (buffer.remaining() < 1) return -1;
        final int b2 = buffer.get();
        return ((c & 0b000_11111) << 6)
            + (b2 & 0b00_111111);
      }
      case 0: // 0_xxxxxxx
        return c;
      default: // 110_xxxxx, 111110_xx, 1111110_x, 11111110, or 11111111
        return -1;
    }
  }

  /**
   * Decodes a code-point from the supplied input stream.
   *
   * @param in the input stream from which to read bytes.
   * @return the decoded code point, {@code -1} when the UTF-8 is malformed.
   * @throws EOFException if the input-stream's end was reached.
   * @throws IOException raised by the input stream, if the reader encounters any error.
   */
  public static int decodeCodePoint(final @NotNull InputStream in) throws IOException {
    // Widen into 32-bit register, single CPU instruction.
    final int c = in.read();
    if (c < 0) throw new EOFException();
    // Invert the register, then count the leading zeros, two CPU instruction.
    final int leadBits = Integer.numberOfLeadingZeros(~c);

    // JIT should create a jump table as the possible value range is limited to 0 to 32.
    // In addition, the order of execution does not matter, therefore,
    // the following code allows the CPU to reorder, execute in parallel,
    // and is beneficial for the branch predictor.
    switch (leadBits) {
      // 11110_xxx 10_xxxxxx 10_xxxxxx 10_xxxxxx
      case 4: {
        final int b2 = in.read();
        final int b3 = in.read();
        final int b4 = in.read();
        if (b2 < 0 || b3 < 0 || b4 < 0) throw new EOFException();
        return ((c & 0b00000_111) << 18)
            + ((b2 & 0b00_111111) << 12)
            + ((b3 & 0b00_111111) << 6)
            + (b4 & 0b00_111111);
      }
      // 1110_xxxx 10_xxxxxx 10_xxxxxx
      case 3: {
        final int b2 = in.read();
        final int b3 = in.read();
        if (b2 < 0 || b3 < 0) throw new EOFException();
        return ((c & 0b0000_1111) << 12)
            + ((b2 & 0b00_111111) << 6)
            + (b3 & 0b00_111111);
      }
      // 110_xxxxx 10_xxxxxx
      case 2: {
        final int b2 = in.read();
        if (b2 < 0) throw new EOFException();
        return ((c & 0b000_11111) << 6)
            + (b2 & 0b00_111111);
      }
      case 0: // 0_xxxxxxx
        return c;
      default: // 110_xxxxx, 111110_xx, 1111110_x, 11111110, or 11111111
        return -1;
    }
  }

  /**
   * Decodes a code-point from the supplied bytes.
   *
   * <h2>Warning</h2>
   * This is a high performance version that does not check for errors! It simply assumes, that the provided byte values are valid and will blindly decode them into a code-point. If the lead-in byte signals a multibyte value, but the other bytes store invalid values, this just leads to totally wrong results.
   *
   * @param b1 the first byte.
   * @return the decoded code point.
   */
  public static int decodeCodePoint(final byte b1) {
    return decodeCodePoint(b1, (byte)-1, (byte)-1, (byte)-1);
  }

  /**
   * Decodes a code-point from the supplied bytes.
   *
   * <h2>Warning</h2>
   * This is a high performance version that does not check for errors! It simply assumes, that the provided byte values are valid and will blindly decode them into a code-point. If the lead-in byte signals a multibyte value, but the other bytes store invalid values, this just leads to totally wrong results.
   *
   * @param b1 the first byte.
   * @param b2 the second byte.
   * @return the decoded code point.
   */
  public static int decodeCodePoint(final byte b1, final byte b2) {
    return decodeCodePoint(b1, b2, (byte)-1, (byte)-1);
  }

  /**
   * Decodes a code-point from the supplied bytes.
   *
   * <h2>Warning</h2>
   * This is a high performance version that does not check for errors! It simply assumes, that the provided byte values are valid and will blindly decode them into a code-point. If the lead-in byte signals a multibyte value, but the other bytes store invalid values, this just leads to totally wrong results.
   *
   * @param b1 the first byte.
   * @param b2 the second byte.
   * @param b3 the third byte.
   * @return the decoded code point.
   */
  public static int decodeCodePoint(final byte b1, final byte b2, final byte b3) {
    return decodeCodePoint(b1, b2, b3, (byte)-1);
  }

  /**
   * Decodes a code-point from the supplied bytes.
   *
   * <h2>Warning</h2>
   * This is a high performance version that does not check for errors! It simply assumes, that the provided byte values are valid and will blindly decode them into a code-point. If the lead-in byte signals a multibyte value, but the other bytes store invalid values, this just leads to totally wrong results.
   *
   * @param b1 the first byte.
   * @param b2 the second byte.
   * @param b3 the third byte.
   * @param b4 the fourth byte.
   * @return the decoded code point.
   */
  public static int decodeCodePoint(final byte b1, final byte b2, final byte b3, final byte b4) {
    // Widen into 32-bit register, single CPU instruction.
    final int c = b1 & 0b1111_1111;
    // Invert the register, then count the leading zeros, two CPU instruction.
    final int leadBits = Integer.numberOfLeadingZeros(~c);

    // JIT should create a jump table as the possible value range is limited to 0 to 32.
    // In addition, the order of execution does not matter, therefore,
    // the following code allows the CPU to reorder, execute in parallel,
    // and is beneficial for the branch predictor.
    switch (leadBits) {
      case 4: // 11110_xxx 10_xxxxxx 10_xxxxxx 10_xxxxxx
        return ((c & 0b00000_111) << 18)
            + ((b2 & 0b00_111111) << 12)
            + ((b3 & 0b00_111111) << 6)
            + (b4 & 0b00_111111);
      case 3: // 1110_xxxx 10_xxxxxx 10_xxxxxx
        return ((c & 0b0000_1111) << 12)
            + ((b2 & 0b00_111111) << 6)
            + (b3 & 0b00_111111);
      case 2: // 110_xxxxx 10_xxxxxx
        return ((c & 0b000_11111) << 6)
            + (b2 & 0b00_111111);
      case 0: // 0_xxxxxxx
        return c;
      default: // 110_xxxxx, 111110_xx, 1111110_x, 11111110, or 11111111
        return -1;
    }
  }

  /**
   * Decode a code-point from the supplied byte-array.
   *
   * <h2>Beware</h2>
   * The method will return two 32-bit values wrapped into a single 64-bit integer. The code-point that has been decoded, and the index of the next byte to decode, please always test {@link #resultGetNextIndex(long)} before reading the actual decoded code-point via {@link #resultGetCodePoint(long)}!
   *
   * @param bytes the bytes array from which to decode the next code-point.
   * @param i the index from where to read the next byte.
   * @return the code-point decoded from the supplied array ({@link #resultGetCodePoint(long)}) and the index of the first byte that was not read ({@link #resultGetNextIndex(long)}), so the index to continue reading from.
   */
  public static long decodeCodePoint(final byte @NotNull [] bytes, final int i) {
    if (i >= bytes.length) return combine(-1, 0);
    // Read a byte, single CPU instruction.
    final byte b1 = bytes[i];
    // Widen into 32-bit register, single CPU instruction.
    final int c = b1 & 0b1111_1111;
    // Invert the register, then count the leading zeros, two CPU instruction.
    final int leadBits = Integer.numberOfLeadingZeros(~c);
    // Uncomment this assertion, IntelliJ will tell you that this is always true, so the compiler knows!
    //assert leadBits >= 0 && leadBits <= 32;
    // Therefore, compiler should create a jump table, because the possible value range is limited to 0 to 32, with only 5 dedicated actions.
    // In addition, the order of execution for addition does not matter, therefore, the code in the switch allows the CPU to reorder,
    // execute in parallel, and the switch should be optimal for the branch predictor. We do not generate any exceptions either, this
    // allows theoretically optimal code performance.
    switch (leadBits) {
      // 11110_xxx 10_xxxxxx 10_xxxxxx 10_xxxxxx
      case 4: {
        final int end = i + 4;
        if (end > bytes.length) return combine(i, -1);
        return combine(end, ((c & 0b00000_111) << 18)
            + ((bytes[i + 1] & 0b00_111111) << 12)
            + ((bytes[i + 2] & 0b00_111111) << 6)
            + (bytes[i + 3] & 0b00_111111)
        );
      }

      // 1110_xxxx 10_xxxxxx 10_xxxxxx
      case 3: {
        final int end = i + 3;
        if (end > bytes.length) return combine(i, -1);
        return combine(end, ((c & 0b0000_1111) << 12)
            + ((bytes[i + 1] & 0b00_111111) << 6)
            + (bytes[i + 2] & 0b00_111111)
        );
      }

      // 110_xxxxx 10_xxxxxx
      case 2: {
        final int end = i + 2;
        if (end > bytes.length) return combine(i, -1);
        return combine(end, ((c & 0b000_11111) << 6)
            + (bytes[i] & 0b00_111111)
        );
      }

      // 0_xxxxxxx
      case 0: {
        return combine(i + 1, c);
      }

      default:
        // Malformed UTF-8 so very likely no UTF-8 or broken encoding.
        return combine(i, -1);
    }
  }

  /**
   * Returns the index and code-point, combined into a 64-bit integer.
   *
   * @param index the index to return.
   * @param codePoint the codepoint to return.
   * @return the index and code-point.
   */
  public static long combine(final int index, final int codePoint) {
    return combineInts(index, codePoint);
  }

  /**
   * Returns the index of the next byte to decode from the result.
   *
   * <h2>Beware:</h2>
   * When the last code-point is decoded, the method does <b>NOT</b> return {@code -1} as next index, but the input length, and when calling {@link #decodeCodePoint(byte[], int)} again, providing the input length as index, then {@code EOF} aka {@code -1} is returned as next index. This means, it is totally safe to first test if the next index is less than {@code 0}, and if it is, to assume that no valid code-point has been decoded, and abort the processing!
   *
   * <p>Therefore, ones it is clear that {@code EOF} has not been hit, the code-point should be read using {@link #resultGetCodePoint(long)}.
   * @param result the result as returned by the {@link #decodeCodePoint(byte[], int)}.
   * @return the index of the next byte that should be read; {@code -1} if EOF is reached, in that case the code-point will be as well {@code -1}.
   */
  public static int resultGetNextIndex(final long result) {
    return highInt(result);
  }

  /**
   * Returns the code-point contained in the result.
   *
   * <h2>Beware</h2>
   * This method must not be called before {@link #resultGetNextIndex(long)}, please always first test for {@code EOF}, before processing the code-point. If the code-point is {@code -1}, then this means that while there is still input left, the encoding is broken, we hit a malformed UTF-8 encoding.
   * @param result the result as returned by the {@link #decodeCodePoint(byte[], int)}.
   * @return the code-point that was decoded, either a positive integer or {@code -1}, if the UTF-8 is malformed.
   */
  public static int resultGetCodePoint(final long result) {
    return lowInt(result);
  }

  /**
   * All UTF-8 encoding or decoding exceptions extend this base exception.
   */
  public static class Utf8Exception extends RuntimeException {
    Utf8Exception() {
    }

    Utf8Exception(String message) {
      super(message);
    }

    Utf8Exception(String message, Throwable cause) {
      super(message, cause);
    }

    Utf8Exception(Throwable cause) {
      super(cause);
    }
  }

  /**
   * Thrown if a code-point is no valid UNICODE code point.
   */
  public static final class InvalidCodePoint extends Utf8Exception {

    private InvalidCodePoint(int codePoint) {
      this.codePoint = codePoint;
    }

    /** The code point that failed to be converted. */
    public final int codePoint;
  }

  /**
   * Thrown if the output reached the defined end.
   */
  public static final class Overflow extends Utf8Exception {

    private Overflow(final int index) {
      this.index = index;
    }

    /** The index at which the error occurred, so the writing to this index failed. */
    public final int index;
  }

  /**
   * Thrown if the input to and UTF-8 decoding contains an invalid encoded code point, so the bytes are no valid UTF-8 encoded string.
   */
  public static final class InvalidEncoding extends Utf8Exception {

    private InvalidEncoding(int index, @NotNull String msg) {
      super(msg);
      this.buffer = null;
      this.in = null;
      this.index = index;
      this.value = 0;
    }

    private InvalidEncoding(byte value) {
      super("Illegal high byte in UTF-8 encoding");
      this.buffer = null;
      this.in = null;
      this.index = -1;
      this.value = value;
    }

    private InvalidEncoding(int index, byte value) {
      super("Illegal high byte in UTF-8 encoding");
      this.buffer = null;
      this.in = null;
      this.index = index;
      this.value = value;
    }

    private InvalidEncoding(@NotNull InputStream in, byte value) {
      super("Illegal high byte in UTF-8 encoding");
      this.buffer = null;
      this.in = in;
      this.index = -1;
      this.value = value;
    }

    private InvalidEncoding(@NotNull ByteBuffer buffer, byte value) {
      super("Illegal high byte in UTF-8 encoding");
      this.buffer = buffer;
      this.in = null;
      this.index = -1;
      this.value = value;
    }

    /**
     * If no {@link #index} is available, because the value was read from a input-stream, this holds the reference to the input-stream.
     */
    public final InputStream in;

    /**
     * If no {@link #index} is available, because the value was read from a buffer, this holds the reference to the buffer.
     */
    public final ByteBuffer buffer;

    /** The index in the byte-array where the illegal code point was found. */
    public final int index;

    /** The value that did not correctly follow UTF-8 specification. */
    public final byte value;
  }
}
