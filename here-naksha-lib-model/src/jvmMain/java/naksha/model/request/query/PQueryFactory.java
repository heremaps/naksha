package naksha.model.request.query;

import org.jetbrains.annotations.NotNull;

public class PQueryFactory {

  private PQueryFactory() {
  }

  public static @NotNull PQuery propertyExistsQuery(final @NotNull String[] propPath) {
    return new PQuery(Property.fromArray(propPath), AnyOp.EXISTS);
  }

  public static @NotNull PQuery propertyEqualsQuery(final @NotNull String[] propPath, String value) {
    return new PQuery(Property.fromArray(propPath), StringOp.EQUALS, value);
  }

  public static @NotNull PQuery propertyEqualsQuery(final @NotNull String[] propPath, Number value) {
    return new PQuery(Property.fromArray(propPath), DoubleOp.EQ, value);
  }

  public static @NotNull PQuery propertyContainsQuery(final @NotNull String[] propPath, String value) {
    return new PQuery(Property.fromArray(propPath), StringOp.CONTAINS, value);
  }
}
