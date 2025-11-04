package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.*;
import static naksha.base.Json.*;
import static naksha.base.JvmUtil.optimalObjectArrayLength;

/**
 * A Java implementation of a sparse JSON array, not thread-safe!
 *
 * @since 3.0
 * @see JsonArray
 * @see JsonArrayProxiable
 * @see JsonArrayProxy
 * @see JsonMap
 * @see JsonMapProxiable
 * @see JsonMapProxy
 */
public final class JsonArray implements List<@Nullable Object>, JsonObject, JsonArrayProxiable {
  /**
   * Creates a new {@link JsonArray} consuming the given elements array. This means, the ownership of the given array is passed over to this {@link JsonArray} instance, so no copy is made. The provider should not continue to use the array!
   * @param elements the array to consume.
   * @param length the amount of valid values within the array, {@code -1} if not known.
   * @return the {@link JsonArray} that uses the given elements.
   * @since 3.0
   */
  public static @NotNull JsonArray wrapElements(@Nullable Object @NotNull [] elements, int length) {
    final JsonArray array = new JsonArray();
    array.elements = elements;
    array.length = length;
    return array;
  }

  /**
   * The internal array representation of the elements.
   * @since 3.0
   */
  public @Nullable Object @NotNull [] elements;

  /**
   * The length of the array, so the amount of valid entries within the elements.
   * @since 3.0
   * @see #size()
   */
  public int length;

  /**
   * All proxies currently being in use.
   * @since 3.0
   */
  @NotNull JsonArrayProxy<?> @Nullable [] proxies;

  @Override
  @SuppressWarnings("unchecked")
  public <E, P extends JsonArrayProxy<E>> @NotNull P proxy(@NotNull Class<P> as, boolean exact) {
    var proxies = this.proxies;
    P proxy;
    if (proxies == null) {
      proxy = Json.newInstance(as);
      proxy.jsonArray = this;
      this.proxies = new JsonArrayProxy[]{ proxy };
      return proxy;
    }
    P match = null;
    for (var p : proxies) {
      final var pClass = p.getClass();
      if (pClass == as) {
        return (P) p;
      }
      if (!exact && as.isAssignableFrom(pClass)) {
        match = (P) p;
      }
    }
    if (match != null) {
      return match;
    }
    proxy = Json.newInstance(as);
    proxy.jsonArray = this;
    proxies = Arrays.copyOf(proxies, proxies.length + 1);
    proxies[proxies.length - 1] = proxy;
    this.proxies = proxies;
    return proxy;
  }

  /**
   * Sets the elements, auto-detects the length.
   * @param elements the new elements to use.
   * @return this.
   */
  public @NotNull JsonArray withElements(@Nullable Object @NotNull [] elements) {
    return withElements(elements, -1);
  }

  /**
   * Sets the elements and the length.
   * @param elements the new elements to use.
   * @param length the amount of valid values in the given elements array.
   * @return this.
   */
  public @NotNull JsonArray withElements(@Nullable Object @NotNull [] elements, int length) {
    this.elements = elements;
    this.length = length;
    return this;
  }

  /**
   * Creates a new empty array.
   * @since 3.0
   */
  public JsonArray() {
    this.elements = EMPTY_ARRAY;
    this.length = 0;
  }

  /**
   * Creates a new empty array of a given capacity, setting length to {@code 0}, and filling the empty slots with {@link Json#UNDEFINED UNDEFINED}.
   * @param capacity the capacity to initialize the array with.
   * @since 3.0
   */
  public JsonArray(int capacity) {
    this(capacity, 0, null);
  }

  /**
   * Creates a new array with a specific capacity, length, and the given initial values in the valid area, the rest will be {@link Json#UNDEFINED UNDEFINED}.
   * @param capacity the capacity to initialize the array with.
   * @param length the length to initialize the array with.
   * @param fillWith the value to fill the array with, normally either {@code null} or {@link Json#UNDEFINED UNDEFINED}.
   * @since 3.0
   */
  public JsonArray(int capacity, int length, @Nullable Object fillWith) {
    assert capacity >= 0;
    assert length >= 0 && length <= capacity;
    this.elements = Json.ensure_size(EMPTY_ARRAY, capacity, false, fillWith);
    if (length < elements.length) Arrays.fill(this.elements, length, capacity, UNDEFINED);
    this.length = length;
  }

  /**
   * Creates a new array by making a copy of the values from given elements array.
   *
   * <p>If no copy should be made, rather use {@link #wrapElements(Object[], int)}.
   * @param elements the elements to copy.
   * @see #wrapElements(Object[], int)
   */
  public <T> JsonArray(@Nullable T @NotNull [] elements) {
    this.elements = Arrays.copyOf(elements, elements.length);
    this.length = elements.length;
  }

  /**
   * Creates a new array by making a copy of the values from given elements array.
   * @param elements the elements to copy.
   * @param fromIndex the index of the first element to copy.
   */
  public <T> JsonArray(@Nullable T @NotNull [] elements, int fromIndex) {
    final var length = elements.length - fromIndex;
    this.elements = new Object[length];
    this.length = length;
    System.arraycopy(elements, fromIndex, this.elements, 0, length);
  }

  /**
   * Creates a new array by making a copy of the values from given elements array.
   * @param elements the elements to copy.
   * @param fromIndex the index of the first element to copy.
   * @param toIndex the index of the first element <b>NOT</b> to copy.
   * @see #wrapElements(Object[], int)
   */
  public <T> JsonArray(@Nullable T @NotNull [] elements, int fromIndex, int toIndex) {
    final var length = toIndex - fromIndex;
    this.elements = new Object[length];
    this.length = length;
    System.arraycopy(elements, fromIndex, this.elements, 0, length);
  }

  @Override
  public int size() {
    return length;
  }

  /**
   * Returns the capacity of the array.
   * @return the capacity of the array.
   * @since 3.0
   */
  public int capacity() {
    return elements.length;
  }

  /**
   * Ensures that there is room at the end of the array for at least the given amount of new elements.
   * @param amount the amount of elements that should have space at the end of the array, before another expansion is needed.
   * @return this.
   * @since 3.0
   */
  public @NotNull JsonArray ensure(int amount) {
    elements = Json.ensure_size(elements, length + amount, false, UNDEFINED);
    return this;
  }

  @Override
  public boolean isEmpty() {
    return length == 0;
  }

  @Override
  public boolean contains(@NotNull Object o) {
    return indexOf(o) >= 0;
  }

  @Override
  public int indexOf(@Nullable Object o) {
    final var elements = this.elements;
    return array_index_of(elements, 0, length, o);
  }

  @Override
  public @NotNull Iterator<@Nullable Object> iterator() {
    return listIterator();
  }

  @Override
  public @Nullable Object @NotNull [] toArray() {
    return Arrays.copyOf(elements, length);
  }

  @Override
  public @Nullable Object set(int index, @Nullable Object element) {
    final var elements = this.elements;
    var oldValue = elements[index];
    elements[index] = element;
    return oldValue;
  }

  @Override
  public void add(@Range(from = 0, to = Integer.MAX_VALUE) int index, @Nullable Object element) {
    final var elements = this.elements;
    final var length = this.length;
    // Append at the end, so index is the new length!
    if (index >= length) {
      final Object[] new_elements;
      if (index >= elements.length) {
        new_elements = ensure_size(elements, index + 1, false, UNDEFINED);
      } else {
        new_elements = elements;
      }
      // This can cause wholes, but that is fine with us.
      new_elements[index] = element;
      this.elements = new_elements;
      this.length = index + 1;
      return;
    }

    // We need to move backwards
    if (length == elements.length) {
      // Array is full, copy into new array.
      final var new_elements = new Object[JvmUtil.optimalObjectArrayLength(length+1)];
      System.arraycopy(elements, 0, new_elements, 0, index);
      new_elements[index] = element;
      System.arraycopy(elements, index, new_elements, index+1, length-index);
      this.elements = new_elements;
    } else {
      // There is space in the array, just copy backward.
      System.arraycopy(elements, index, elements, index + 1, length - index);
      elements[index] = element;
    }
    this.length++;
  }

  @Override
  public boolean add(@Nullable Object o) {
    add(length, o);
    return true;
  }

  @Override
  public boolean remove(@Nullable Object o) {
    final int index = indexOf(o);
    if (index >= 0) {
        remove(index);
        return true;
    }
    return false;
  }

  @Override
  public boolean addAll(@NotNull Collection c) {
    return addAll(length, c);
  }

  @Override
  public boolean addAll(final int index, @NotNull Collection c) {
    final var current_length = this.length;
    if (index < 0 || index > current_length) {
        throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+elements.length);
    }
    final int c_length = c.size();
    if (c_length == 0) return false;
    final int new_length = current_length + c_length;
    if (index == current_length) { // Append only.
      final var elements = ensure_size(this.elements, new_length, false, UNDEFINED);
      int i = index;
      for (final Object element : c) {
        elements[i++] = element;
      }
      assert i == new_length;
      this.elements = elements;
      this.length = new_length;
      return true;
    }
    // Creating a new array is much faster.
    final int new_capacity = optimalObjectArrayLength(new_length);
    final var elements = new Object[new_capacity];
    // Copy elements that will stay as they are.
    System.arraycopy(this.elements, 0, elements, 0, index);
    // Add new elements.
    int i = index;
    for (final Object element : c) {
      elements[i++] = element;
    }
    // Append elements in-front of which we should add.
    System.arraycopy(this.elements, index, elements, i, current_length - index);
    // Fill rest with UNDEFINED.
    if (new_capacity > new_length) {
      Arrays.fill(elements, new_length, new_capacity - new_length, UNDEFINED);
    }
    this.elements = elements;
    this.length = new_length;
    return true;
  }

  @Override
  public void clear() {
    if (elements.length > MAX_EMPTY_CAPACITY) {
      elements = EMPTY_ARRAY;
    } else {
      Arrays.fill(elements, UNDEFINED);
    }
    length = 0;
  }

  /**
   * Compact the array, so that no memory is wasted.
   * @return true if the array was compacted; false if the array is already compact.
   * @since 3.0
   */
  public boolean compact() {
    final var length = this.length;
    if (length < elements.length) {
      elements = Arrays.copyOf(elements, length);
      return true;
    }
    return false;
  }

  @Override
  public @Nullable Object get(int index) {
    if (index >= length) throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+size());
    // Leave the rest of th checks to the JVM, it will do it anyway.
    return elements[index];
  }

  @Override
  public @Nullable Object remove(int index) {
    final var elements = this.elements;
    final var length = this.length;
    if (index < 0 || index >= length) return null;
    final var removed = elements[index];
    elements[index] = TOMBSTONE;
    if (index == length - 1) {
      // We removed the last element, no need to copy elements.
      this.length = length - 1;
    } else {
      this.length = array_compact(elements, length);
    }
    return removed;
  }

  @Override
  public int lastIndexOf(@Nullable Object o) {
    return array_last_index_of(elements, 0, size(), o);
  }

  @Override
  public @NotNull ListIterator<@Nullable Object> listIterator() {
    return listIterator(0);
  }

  @Override
  public @NotNull ListIterator<@Nullable Object> listIterator(final int fromIndex) {
    return new ListIterator<>() {
      private int index = fromIndex;
      private boolean canRemove = false;
      private boolean lastMoveWasNext = false;

      @Override
      public boolean hasNext() {
        return index < length;
      }

      @Override
      public @Nullable Object next() {
        if (!hasNext()) throw new NoSuchElementException();
        lastMoveWasNext = true;
        canRemove = true;
        return elements[index++];
      }

      @Override
      public boolean hasPrevious() {
        return index > 0;
      }

      @Override
      public @Nullable Object previous() {
        if (!hasPrevious()) throw new NoSuchElementException();
        lastMoveWasNext = false;
        canRemove = true;
        return elements[--index];
      }

      @Override
      public int nextIndex() {
        return index;
      }

      @Override
      public int previousIndex() {
        return index-1;
      }

      @Override
      public void remove() {
        if (!canRemove) throw new IllegalStateException();
        if (lastMoveWasNext) {
          JsonArray.this.remove(--index);
        } else { // previous
          JsonArray.this.remove(index);
        }
        canRemove = false;
      }

      @Override
      public void set(Object o) {
        if (!lastMoveWasNext && index == 0) {
          throw new IllegalStateException();
        }
        if (lastMoveWasNext) {
          elements[index-1] = o;
        } else { // previous
          elements[index] = o;
        }
      }

      @Override
      public void add(Object o) {
        JsonArray.this.add(index, o);
        canRemove = false;
      }
    };
  }

  @Override
  public @NotNull List<@Nullable Object> subList(int fromIndex, int toIndex) {
    return new SubList(fromIndex, toIndex);
  }

  private class SubList extends AbstractList<@Nullable Object> {
    private final int offset;
    private final int size;

    SubList(int fromIndex, int toIndex) {
      if (fromIndex < 0 || toIndex > length || fromIndex > toIndex) {
        throw new IndexOutOfBoundsException("fromIndex: "+fromIndex+", toIndex: "+toIndex+", JsonArray size: "+ length);
      }
      this.offset = fromIndex;
      this.size = toIndex - fromIndex;
    }

    @Override
    public Object get(int index) {
      if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Sublist of JsonArray: get index "+index+" out of bounds 0.."+(size-1));
      return elements[offset + index];
    }

    @Override
    public Object set(int index, Object element) {
      if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Sublist of JsonArray: set index "+index+" out of bounds 0.."+(size-1));
      Object old = elements[offset + index];
      elements[offset + index] = element;
      return old;
    }

    @Override
    public int size() {
      return size;
    }
  }

  @Override
  public boolean retainAll(@NotNull Collection c) {
      final var elements = this.elements;
      final var length = this.length;
      int removed = 0;
      for (int i = 0; i < length; i++ ) {
          boolean match = false;
          for (Object o : c) {
              if (Objects.equals(elements[i], o)) {
                  match = true;
                  break;
              }
          }
          if (!match) {
              elements[i] = TOMBSTONE;
              removed++;
          }
      }
      if (removed == 0) {
          return false;
      }
      this.length = array_compact(elements, length);
      return true;
  }

  @Override
  public boolean removeAll(@NotNull Collection c) {
    final var elements = this.elements;
    final var length = this.length;
    int removed = 0;
    for (final Object o : c) {
      for (int i = 0; i < length; i++) {
        if (o != null && o == elements[i]) {
          elements[i] = TOMBSTONE;
          removed++;
        }
      }
    }
    if (removed == 0) {
      return false;
    }
    this.length = array_compact(elements, length);
    return true;
  }

  @Override
  public boolean containsAll(@NotNull Collection c) {
    for (Object o : c) {
        if (!contains(o)) {
            return false;
        }
    }
    return true;
  }

  @SuppressWarnings({"unchecked", "SuspiciousSystemArraycopy"})
  @Override
  public <T> T @NotNull [] toArray(@Nullable T @NotNull [] a) {
    final var klass = a.getClass();
    if (klass.isPrimitive()) throw new ArrayStoreException("References can't be copied into primitives");
    final var elements = this.elements;
    final int length = size();
    if (a.length < length) {
        return (T[]) Arrays.copyOf(elements, length, klass);
    }
    System.arraycopy(elements, 0, a, 0, length);
    if (a.length > length) {
        Arrays.fill(a, length, a.length, null);
    }
    return a;
  }
}