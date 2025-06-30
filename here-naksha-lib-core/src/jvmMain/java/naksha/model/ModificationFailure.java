package naksha.model;

import naksha.base.AnyObject;
import naksha.base.NotNullProperty;
import naksha.base.PlatformType;
import org.jetbrains.annotations.NotNull;

import static naksha.base.NakshaBaseKt.Long_TYPE;
import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

public class ModificationFailure extends AnyObject {
  public static final PlatformType<ModificationFailure> TYPE = forClass(ModificationFailure.class);
  public static final String ID = "id";
  private static final NotNullProperty<ModificationFailure, String> ID$ = new NotNullProperty<>(String_TYPE, ID);
  public static final String POSITION = "position";
  private static final NotNullProperty<ModificationFailure, Long> POSITION$ = new NotNullProperty<>(Long_TYPE, POSITION);
  public static final String MESSAGE = "message";
  private static final NotNullProperty<ModificationFailure, String> MESSAGE$ = new NotNullProperty<>(String_TYPE, MESSAGE);

  public @NotNull String getId() {
    return ID$.getValue(this);
  }

  public void setId(@NotNull String id) {
    ID$.setValue(this, ID, id);
  }

  @SuppressWarnings("unused")
  public @NotNull Long getPosition() {
    return POSITION$.getValue(this);
  }

  @SuppressWarnings("WeakerAccess")
  public void setPosition(@NotNull Long position) {
    POSITION$.setValue(this, POSITION, position);
  }

  @SuppressWarnings("unused")
  public @NotNull String getMessage() {
    return MESSAGE$.getValue(this);
  }

  @SuppressWarnings("WeakerAccess")
  public void setMessage(@NotNull String message) {
    MESSAGE$.setValue(this, message);
  }
}
