package naksha.base;

import org.jetbrains.annotations.NotNull;

public class JsonError {
  public JsonError(@NotNull String message, byte[] utf8_json, int line, int column) {
    this.message = message;
    this.utf8_json = utf8_json;
    this.line = line;
    this.column = column;
  }
  public final @NotNull String message;
  public final byte[] utf8_json;
  public final int line;
  public final int column;
}