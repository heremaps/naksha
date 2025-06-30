package naksha.model;

import naksha.base.ListProxy;
import naksha.base.PlatformType;

import java.util.List;

import static naksha.base.Platform.forClass;

public class ModificationFailureList extends ListProxy<ModificationFailure> {
  public static final PlatformType<ModificationFailureList> TYPE = forClass(ModificationFailureList.class);

  public ModificationFailureList() {
    super(ModificationFailure.TYPE);
  }

  public ModificationFailureList(List<ModificationFailure> failures) {
    this();
    addAll(failures);
  }
}
