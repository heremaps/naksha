package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JsonArray implements List<Object> {
    // The internal array representation for mixed case.
    private @NotNull Object[] list;
    // The internal array representation for double values case. If the array contains only double values, this will be non-null.
    private double @Nullable [] doubleList;
    private static final Object[] EMPTY = new Object[0];

    public JsonArray() {
        this.list = EMPTY;
        this.doubleList = null;
    }

    @Override
    public int size() {
        if (doubleList != null) {
            return doubleList.length;
        }
        return list.length;
    }

    @Override
    public boolean isEmpty() {
        // doubleList always start with 1 or more elements
        return doubleList != null || list == EMPTY;
    }

    @Override
    public boolean contains(@NotNull Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int indexOf(@Nullable Object o) {
        if (o instanceof Double && doubleList != null) {
            var localDoubleList = doubleList;
            double doubleValue = (Double) o;
            for (int i = 0; i < localDoubleList.length; i++) {
                if (localDoubleList[i] == doubleValue) {
                    return i;
                }
            }
            return -1;
        }
        var localList = list;
        for (int i = 0; i < localList.length; i++) {
            if (internalEquals(o,localList[i])) {
                return i;
            }
        }
        return -1;
    }

    @NotNull
    @Override
    public Iterator<Object> iterator() {
        //TODO
        return null;
    }

    @Override
    public Object @NotNull [] toArray() {
        if (doubleList != null) {
            final var localDoubleList = doubleList;
            Object[] result = new Object[localDoubleList.length];
            for (int i = 0; i < localDoubleList.length; i++) {
                result[i] = localDoubleList[i];
            }
            return result;
        }
        return Arrays.copyOf(list, list.length);
    }

    @Override
    public Object set(int index, Object element) {
        var localDoubleList = doubleList;
        if (localDoubleList != null) {
            if (element instanceof Double) {
                if (index < 0 || index >= localDoubleList.length) {
                    throw new IndexOutOfBoundsException("Index: " + index + ", JsonArray size: " + localDoubleList.length);
                }
                var oldValue = localDoubleList[index];
                localDoubleList[index] = (Double) element;
                return oldValue;
            }
            // First non-double being set
            var localList = new Object[localDoubleList.length];
            for (int i = 0; i < index; i++) {
                localList[i] = localDoubleList[i];
            }
            localList[index] = element;
            for (int i = index+1; i < localDoubleList.length; i++) {
                localList[i] = localDoubleList[i];
            }
            list = localList;
            doubleList = null; // Mixed types now
            return localDoubleList[index];
        }
        var oldValue = list[index];
        list[index] = element;
        return oldValue;
    }

    @Override
    public void add(int index, Object element) {
        if (element instanceof Double) {
            if (doubleList != null) {
                var localDoubleList = doubleList;
                if (index < 0 || index > localDoubleList.length) {
                    throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+localDoubleList.length);
                }
                var newDoubleList = new double[localDoubleList.length + 1];
                System.arraycopy(localDoubleList, 0, newDoubleList, 0, index);
                newDoubleList[index] = (Double) element;
                if (index < localDoubleList.length) // not adding at end
                {
                    System.arraycopy(localDoubleList, index, newDoubleList, index + 1, localDoubleList.length-index);
                }
                doubleList = newDoubleList;
                return;
            } else if (list == EMPTY) { // First element and it's a double
                var localDoubleList = new double[1];
                localDoubleList[0] = (Double) element;
                doubleList = localDoubleList;
                return;
            }
        }
        // Either adding double to existing mixed list, or adding non-double
        Object[] newList;
        if (doubleList != null) { // First non-double being added
            var localDoubleList = doubleList;
            newList = new Object[localDoubleList.length+1];
            for (int i = 0; i < index; i++) {
                newList[i] = localDoubleList[i];
            }
            for (int i=index+1; i<newList.length; i++) {
                newList[i] = localDoubleList[i];
            }
            doubleList = null; // Mixed types now
        } else {
            var localList = list;
            newList = new Object[localList.length + 1];
            System.arraycopy(localList, 0, newList, 0, index);
            if (index < localList.length) { // not adding at end
                System.arraycopy(localList, index, newList, index + 1, localList.length - index);
            }
        }
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
//        // Either adding double to existing mixed list, or adding non-double
//        Object[] newList;
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
//            var localList = list;
//            newList = new Object[localList.length + 1];
//            System.arraycopy(localList, 0, newList, 0, index);
//            if (index < localList.length) { // not adding at end
//                System.arraycopy(localList, index, newList, index + 1, localList.length - index);
//            }
//        }
//        newList[index] = element;
//        list = newList;
        //TODO
        return false;
    }

    @Override
    public void clear() {
        doubleList = null;
        list = EMPTY;
    }

    @Override
    public Object get(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(index);
        }
        var localDoubleList = doubleList;
        if (localDoubleList != null) {
            if (index >= localDoubleList.length) {
                throw new IndexOutOfBoundsException(index+" >= "+localDoubleList.length);
            }
            return localDoubleList[index];
        }
        var localList = list;
        if (index >= localList.length) {
            throw new IndexOutOfBoundsException(index+" >= "+localList.length);
        }
        return localList[index];
    }

    @Override
    public Object remove(int index) {
        if (doubleList != null) {
            var localDoubleList = doubleList;
            if (index < 0 || index >= localDoubleList.length) {
                throw new IndexOutOfBoundsException("Index: "+index+", JsonArray size: "+localDoubleList.length);
            }
            if (localDoubleList.length == 1) { // Removing the only element
                doubleList = null;
                return localDoubleList[0];
            }
            var newDoubleList = new double[localDoubleList.length - 1];
            System.arraycopy(localDoubleList, 0, newDoubleList, 0, index);
            if (index < localDoubleList.length - 1) { // not last element removed
                System.arraycopy(localDoubleList, index + 1, newDoubleList, index, localDoubleList.length - index - 1);
            }
            doubleList = newDoubleList;
            return localDoubleList[index];
        }
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

    private boolean internalEquals(@Nullable Object a, @Nullable Object b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    @Override
    public int lastIndexOf(Object o) {
        //TODO
        return 0;
    }

    @NotNull
    @Override
    public ListIterator listIterator() {
        //TODO
        return null;
    }

    @NotNull
    @Override
    public ListIterator listIterator(int index) {
        //TODO
        return null;
    }

    @NotNull
    @Override
    public List subList(int fromIndex, int toIndex) {
        //TODO
        return List.of();
    }

    @Override
    public boolean retainAll(@NotNull Collection c) {
        //TODO
        return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection c) {
        //TODO
        return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection c) {
        //TODO
        return false;
    }

    @NotNull
    @Override
    public Object[] toArray(@NotNull Object[] a) {
        //TODO
        return new Object[0];
    }
}
