package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JsonArray implements List<Object> {
    // The internal array representation for mixed case.
    private @Nullable Object @NotNull [] list;
    // The internal array representation for double values case. If the array contains only double values, this will be non-null.
    // Not used for now.
//    private double @Nullable [] doubleList;
    private static final Object[] EMPTY = new Object[0];

    public JsonArray() {
        this.list = EMPTY;
//        this.doubleList = null;
    }

    JsonArray(@Nullable Object @NotNull [] elements) {
      this.list = elements;
//      this.doubleList = null;
    }

    @Override
    public int size() {
//        if (doubleList != null) {
//            return doubleList.length;
//        }
        return list.length;
    }

    @Override
    public boolean isEmpty() {
        // doubleList always start with 1 or more elements
//        return doubleList != null || list == EMPTY;
        return list == EMPTY;
    }

    @Override
    public boolean contains(@NotNull Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int indexOf(@Nullable Object o) {
//        if (o instanceof Double && doubleList != null) return indexOfDouble((Double) o,1);
        return indexOfObject(o, 1);
    }

//    private int indexOfDouble(double value, int step) {
//        var localDoubleList = doubleList;
//        int i;
//        if (step > 0) {
//            i = 0;
//        } else {
//            i = localDoubleList.length - 1;
//        }
//        for (; step > 0 ? i < localDoubleList.length : i >= 0; i += step) {
//            if (localDoubleList[i] == value) return i;
//        }
//        return -1;
//    }

    private int indexOfObject(Object value, int step) {
        var localList = list;
        int i;
        if (step > 0) {
            i = 0;
        } else {
            i = localList.length - 1;
        }
        for (; step > 0 ? i < localList.length : i >= 0; i += step) {
            if (Objects.equals(value,localList[i])) return i;
        }
        return -1;
    }

    @NotNull
    @Override
    public Iterator<Object> iterator() {
        return listIterator();
    }

    @Override
    public Object @NotNull [] toArray() {
//        if (doubleList != null) {
//            final var localDoubleList = doubleList;
//            Object[] result = new Object[localDoubleList.length];
//            for (int i = 0; i < localDoubleList.length; i++) {
//                result[i] = localDoubleList[i];
//            }
//            return result;
//        }
        return Arrays.copyOf(list, list.length);
    }

    @Override
    public Object set(int index, Object element) {
//        var localDoubleList = doubleList;
//        if (localDoubleList != null) {
//            if (element instanceof Double) {
//                if (index < 0 || index >= localDoubleList.length) {
//                    throw new IndexOutOfBoundsException("Index: " + index + ", JsonArray size: " + localDoubleList.length);
//                }
//                var oldValue = localDoubleList[index];
//                localDoubleList[index] = (Double) element;
//                return oldValue;
//            }
//            // First non-double being set
//            var localList = new Object[localDoubleList.length];
//            for (int i = 0; i < index; i++) {
//                localList[i] = localDoubleList[i];
//            }
//            localList[index] = element;
//            for (int i = index+1; i < localDoubleList.length; i++) {
//                localList[i] = localDoubleList[i];
//            }
//            list = localList;
//            doubleList = null; // Mixed types now
//            return localDoubleList[index];
//        }
        var localList = list;
        if (index < 0 || index >= localList.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", JsonArray size: " + localList.length);
        }
        var oldValue = localList[index];
        localList[index] = element;
        return oldValue;
    }

    @Override
    public void add(int index, Object element) {
//        if (element instanceof Double) {
//            if (doubleList != null) {
//                var localDoubleList = doubleList;
//                if (index < 0 || index > localDoubleList.length) {
//                    throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+localDoubleList.length);
//                }
//                var newDoubleList = new double[localDoubleList.length + 1];
//                System.arraycopy(localDoubleList, 0, newDoubleList, 0, index);
//                newDoubleList[index] = (Double) element;
//                if (index < localDoubleList.length) // not adding at end
//                {
//                    System.arraycopy(localDoubleList, index, newDoubleList, index + 1, localDoubleList.length-index);
//                }
//                doubleList = newDoubleList;
//                return;
//            } else if (list == EMPTY) { // First element and it's a double
//                var localDoubleList = new double[1];
//                localDoubleList[0] = (Double) element;
//                doubleList = localDoubleList;
//                return;
//            }
//        }
        // Either adding double to existing mixed list, or adding non-double
        var localList = list;
        if (index < 0 || index > localList.length) {
            throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+localList.length);
        }
        Object[] newList;
//        if (doubleList != null) { // First non-double being added
//            var localDoubleList = doubleList;
//            newList = new Object[localDoubleList.length+1];
//            for (int i = 0; i < index; i++) {
//                newList[i] = localDoubleList[i];
//            }
//            for (int i=index+1; i<newList.length; i++) {
//                newList[i] = localDoubleList[i];
//            }
//            doubleList = null; // Mixed types now
//        } else {
            newList = new Object[localList.length + 1];
            System.arraycopy(localList, 0, newList, 0, index);
            System.arraycopy(localList, index, newList, index + 1, localList.length - index);
//        }
        newList[index] = element;
        list = newList;
    }

    @Override
    public boolean add(@Nullable Object o) {
        add(list.length, o);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        var index = indexOf(o);
        if (index >= 0) {
            remove(index);
            return true;
        }
        return false;
    }

    @Override
    public boolean addAll(@NotNull Collection c) {
        return addAll(list.length, c);
    }

    @Override
    public boolean addAll(int index, @NotNull Collection c) {
        var localList = list;
        if (index < 0 || index > localList.length) {
            throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+localList.length);
        }
        Object[] newList;
        newList = new Object[localList.length + c.size()];
        System.arraycopy(localList, 0, newList, 0, index);
        System.arraycopy(localList, index, newList, index + c.size(), localList.length - index);
        for (Object element : c) {
            newList[index++] = element;
        }
        list = newList;
        return true;
    }

    @Override
    public void clear() {
//        doubleList = null;
        list = EMPTY;
    }

    @Override
    public Object get(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(index);
        }
//        var localDoubleList = doubleList;
//        if (localDoubleList != null) {
//            if (index >= localDoubleList.length) {
//                throw new IndexOutOfBoundsException(index+" >= "+localDoubleList.length);
//            }
//            return localDoubleList[index];
//        }
        var localList = list;
        if (index >= localList.length) {
            throw new IndexOutOfBoundsException(index+" >= "+localList.length);
        }
        return localList[index];
    }

    @Override
    public Object remove(int index) {
//        if (doubleList != null) {
//            var localDoubleList = doubleList;
//            if (index < 0 || index >= localDoubleList.length) {
//                throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+localDoubleList.length);
//            }
//            if (localDoubleList.length == 1) { // Removing the only element
//                doubleList = null;
//                return localDoubleList[0];
//            }
//            var newDoubleList = new double[localDoubleList.length - 1];
//            System.arraycopy(localDoubleList, 0, newDoubleList, 0, index);
//            if (index < localDoubleList.length - 1) { // not last element removed
//                System.arraycopy(localDoubleList, index + 1, newDoubleList, index, localDoubleList.length - index - 1);
//            }
//            doubleList = newDoubleList;
//            return localDoubleList[index];
//        }
        var localList = list;
        if (index < 0 || index >= localList.length) {
            throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+localList.length);
        }
        if (localList.length == 1) { // Removing the only element
            list = EMPTY;
            return localList[0];
        }
        var newList = new Object[localList.length - 1];
        System.arraycopy(localList, 0, newList, 0, index);
        if (index < localList.length - 1) { // not last element removed
            System.arraycopy(localList, index + 1, newList, index, localList.length - index - 1);
        }
        list = newList;
        return localList[index];
    }

    @Override
    public int lastIndexOf(Object o) {
//        if (o instanceof Double && doubleList != null) return indexOfDouble((Double) o,-1);
        return indexOfObject(o, -1);
    }

    @NotNull
    @Override
    public ListIterator<Object> listIterator() {
        return listIterator(0);
    }

    @NotNull
    @Override
    public ListIterator<Object> listIterator(int index) {
        if (index < 0 || index >= list.length) {
            throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+list.length);
        }
        return new ListIterator<>() {
            private int index = 0;
            private boolean canRemove = false;
            private boolean lastMoveWasNext = false;
            @Override
            public boolean hasNext() {
                return index < list.length;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                lastMoveWasNext = true;
                canRemove = true;
                return list[index++];
            }

            @Override
            public boolean hasPrevious() {
                return index > 0;
            }

            @Override
            public Object previous() {
                if (!hasPrevious()) {
                    throw new NoSuchElementException();
                }
                lastMoveWasNext = false;
                canRemove = true;
                return list[--index];
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
                if (!canRemove) {
                    throw new IllegalStateException();
                }
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
                    list[index-1] = o;
                } else { // previous
                    list[index] = o;
                }
            }

            @Override
            public void add(Object o) {
                JsonArray.this.add(index, o);
                canRemove = false;
            }
        };
    }

    @NotNull
    @Override
    public List<Object> subList(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > list.length || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException("fromIndex: "+fromIndex+", toIndex: "+toIndex+", JsonArray size: "+list.length);
        }
        return new SubList(list, fromIndex, toIndex);
    }

    private static class SubList extends AbstractList<Object> {
        private final Object[] array;
        private final int offset;
        private final int size;

        SubList(Object[] array, int offset, int toIndex) {
            this.array = array;
            this.offset = offset;
            this.size = toIndex - offset;
        }

        @Override
        public Object get(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Sublist of JsonArray: get index "+index+" out of bounds 0.."+(size-1));
            return array[offset + index];
        }

        @Override
        public Object set(int index, Object element) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Sublist of JsonArray: set index "+index+" out of bounds 0.."+(size-1));
            Object old = array[offset + index];
            array[offset + index] = element;
            return old;
        }

        @Override
        public int size() {
            return size;
        }
    }

    @Override
    public boolean retainAll(@NotNull Collection c) {
        var localList = list;
        int countToRemove = 0;
        for (int i = 0; i < localList.length; i++ ) {
            boolean match = false;
            for (Object o : c) {
                if (Objects.equals(localList[i], o)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                localList[i] = TOMBSTONE;
                countToRemove++;
            }
        }
        if (countToRemove == 0) {
            return false;
        }
        var newList = new Object[localList.length - countToRemove];
        countToRemove = 0; // reuse countToRemove as new index
        for (Object o : localList) {
            if (o != TOMBSTONE) {
                newList[countToRemove++] = o;
            }
        }
        list = newList;
        return true;
    }

    private static final Object TOMBSTONE = new Object();

    @Override
    public boolean removeAll(@NotNull Collection c) {
        var localList = list;
        int count = 0;
        for (Object o : c) {
            for (int i = 0; i < localList.length; i++ ) {
                if (Objects.equals(o, localList[i])) {
                    localList[i] = TOMBSTONE;
                    count++;
                }
            }
        }
        if (count == 0) {
            return false;
        }
        var newList = new Object[localList.length - count];
        count = 0; // reuse count as new index
        for (Object o : localList) {
            if (o != TOMBSTONE) {
                newList[count++] = o;
            }
        }
        list = newList;
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

    @Override
    public <T> T @NotNull [] toArray(@Nullable T @NotNull [] a) {
        var type = a.getClass().getComponentType();
        if (type != Object.class) {
            throw new ArrayStoreException("JsonArray.toArray(): only Object[] is supported.");
        }
        var localList = list;
        if (a.length < localList.length) {
            return (T[]) Arrays.copyOf(localList, localList.length, a.getClass());
        }
        System.arraycopy(localList, 0, a, 0, localList.length);
        if (a.length > localList.length) {
            a[localList.length] = null;
        }
        return a;
    }
}
