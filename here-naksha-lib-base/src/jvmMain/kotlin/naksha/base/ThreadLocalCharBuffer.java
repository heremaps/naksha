package naksha.base;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * A thread-local character buffer, with resize capabilities.
 */
public final class ThreadLocalCharBuffer {

  /** The thread-local instance. */
  public static final ThreadLocal<@NotNull ThreadLocalCharBuffer> instance = ThreadLocal.withInitial(ThreadLocalCharBuffer::new);

  /** Amount of chars that fit into a normal CPU page of 4 KiB ({@code 2048}). */
  private static final int PAGE_SIZE = 4096 >> 1;

  /** The amount of bit to shift a number to multiply or divide by page size in chars ({@code 11}). */
  private static final int PAGE_SIZE_SHIFT = 11; // page in bytes = 2^12, therefore 2^11 is size in chars

  /** Amount of chars that the JVM header allocates for a char-array on a 64-bit JVM (8 byte mark, 8 byte class, 4 byte length = {@code 10}). */
  private static final int JVM_HEADER_SIZE = (8 + 8 + 4) >> 1;

  /** Amount of chars that fit into the first page of a char array ({@code 2038}). */
  private static final int FIRST_PAGE_SIZE = PAGE_SIZE - JVM_HEADER_SIZE;

  /** The maximal amount of characters the thread-local buffer will keep in the thread-local cache, by default 16 MiB. */
  private static final int MAX_CACHE_SIZE_IN_CHARS = (16_777_216 >> 1) - JVM_HEADER_SIZE;

  private ThreadLocalCharBuffer() {
    this.charsRef = new WeakReference<>(new char[FIRST_PAGE_SIZE]);
  }

  private @NotNull WeakReference<char[]> charsRef;

  /**
   * Calculate the optimal char-array size to fit into CPU pages.
   * @param requested_capacity The amount of byte needed.
   * @return the amount of byte to allocate.
   */
  private int optimalCapacity(int requested_capacity) {
    if (requested_capacity <= FIRST_PAGE_SIZE) return FIRST_PAGE_SIZE;
    // For 2039 we expect this to be 2, resulting in a buffer of size 4086 (two pages of 4096 chars, minus header, 10 chars).
    final int total_pages = (requested_capacity + JVM_HEADER_SIZE + PAGE_SIZE - 1) >> PAGE_SIZE_SHIFT;
    assert total_pages >= 2;
    return FIRST_PAGE_SIZE + ((total_pages-1) << PAGE_SIZE_SHIFT);
  }

  /**
   * Returns the currently allocated character buffer of whatever size it is.
   * @return the currently allocated character buffer of whatever size it is.
   */
  public char @NotNull [] get() {
    return alloc(FIRST_PAGE_SIZE);
  }

  /**
   * Returns a new character buffer minimally of the given size, does not clear the values within.
   * @param capacity the minimal capacity needed.
   * @return the character buffer with minimally the requested capacity and potentially random data in it.
   */
  public char @NotNull [] alloc(int capacity) {
    final var charsRef = this.charsRef;
    var chars = charsRef.get();
    if (chars == null) {
      chars = new char[optimalCapacity(capacity)];
      this.charsRef = new WeakReference<>(chars);
      return chars;
    }
    if (chars.length < capacity) {
      chars = new char[optimalCapacity(capacity)];
      this.charsRef = new WeakReference<>(chars);
    }
    return chars;
  }

  /**
   * Ensures that the given character buffer a capacity that is enough to read/write the given index. Normally, this method should be called before accessing the given index within the character-buffer. This expects that {@code chars} is the cached buffer of this thread-local! It will copy the buffer, and store it in thread locals, it not too big.
   * @param index the index to read/write.
   * @param chars Expected to be the current character buffer.
   * @return the char-buffer that is of the desired size.
   */
  public char @NotNull [] ensure(int index, char @NotNull [] chars) {
    if (index < chars.length) {
      return chars;
    }
    // We do not resize linearly, but try to stay within the max size.
    int new_size = optimalCapacity( (int)((index+1) * 1.5) );
    if (new_size > MAX_CACHE_SIZE_IN_CHARS && index < MAX_CACHE_SIZE_IN_CHARS) {
      new_size = MAX_CACHE_SIZE_IN_CHARS;
    }
    chars = Arrays.copyOf(chars, new_size);
    if (chars.length < MAX_CACHE_SIZE_IN_CHARS) {
      this.charsRef = new WeakReference<>(chars);
    }
    return chars;
  }
}
