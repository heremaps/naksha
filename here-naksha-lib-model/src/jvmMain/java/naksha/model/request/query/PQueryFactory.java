package naksha.model.request.query;

import org.jetbrains.annotations.NotNull;

public class PQueryFactory {

  private PQueryFactory() {
  }

  public static @NotNull PQuery propertyExistsQuery(final @NotNull String[] propPath) {
    return new PQuery(new Property(propPath), AnyOp.EXISTS);
  }

  public static @NotNull PQuery propertyEqualsQuery(final @NotNull String[] propPath, String value) {
    return new PQuery(new Property(propPath), StringOp.EQUALS, value);
  }

  public static @NotNull PQuery propertyEqualsQuery(final @NotNull String[] propPath, Number value) {
    return new PQuery(new Property(propPath), DoubleOp.EQ, value);
  }

  public static @NotNull PQuery propertyContainsQuery(final @NotNull String[] propPath, String value) {
    return new PQuery(new Property(propPath), StringOp.CONTAINS, value);
  }
}
