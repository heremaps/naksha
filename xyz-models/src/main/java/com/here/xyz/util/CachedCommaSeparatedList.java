package com.here.xyz.util;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CachedCommaSeparatedList {
  protected volatile String cachedValue;
  protected List<String> cachedList;

  public @NotNull List<@NotNull String> get(@Nullable String value) {
    //noinspection StringEquality
    if (cachedValue == value) {
      return cachedList;
    }
    synchronized (this) {
      //noinspection StringEquality
      if (cachedValue == value) {
        return cachedList;
      }
      final List<String> list;
      if (value != null && value.length() > 0) {
        final String[] values = value.split(",");
        list = new ArrayList<>(values.length);
        for (String v : values) {
          v = v.trim();
          if (v.length() > 0)
            list.add(v);
        }
      } else {
        list = new ArrayList<>();
      }
      cachedValue = value;
      cachedList = list;
      return list;
    }
  }
}
