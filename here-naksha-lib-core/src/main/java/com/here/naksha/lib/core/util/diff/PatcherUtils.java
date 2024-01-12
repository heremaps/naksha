package com.here.naksha.lib.core.util.diff;

import java.util.Iterator;
import java.util.Map.Entry;

public class PatcherUtils {
    public static Difference removeAllRemoveOp(Difference difference) {
        if (difference instanceof RemoveOp) {
            return null;
        } else if (difference instanceof ListDiff) {
            final ListDiff listdiff = (ListDiff) difference;
            final Iterator<Difference> iterator = listdiff.iterator();
            while (iterator.hasNext()) {
                Difference next = iterator.next();
                if (next == null) continue;
                next = removeAllRemoveOp(next);
                if (next == null) iterator.remove();
            }
            return listdiff;
        } else if (difference instanceof MapDiff) {
            final MapDiff mapdiff = (MapDiff) difference;
            final Iterator<Entry<Object, Difference>> iterator =
                    mapdiff.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<Object, Difference> next = iterator.next();
                next.setValue(removeAllRemoveOp(next.getValue()));
                if (next.getValue() == null) iterator.remove();
            }
            return mapdiff;
        }
        return difference;
    }
}
