package naksha.base;

import org.jetbrains.annotations.NotNull;

public class JsonError extends RuntimeException {
  public JsonError(@NotNull String message, byte[] bytes, int index, int line, int column) {
    super(message);
    this.bytes = bytes;
    this.index = index;
    this.line = line;
    this.column = column;
  }

  public final byte[] bytes;
  public final int index;
  public final int line;
  public final int column;
}