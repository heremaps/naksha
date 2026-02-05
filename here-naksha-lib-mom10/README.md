## lib-mom10 module

The job of this module is to provide Naksha ecosystem with tools needed for correct MOM 10 handling.

Naksha itself relies heavily on `@ns:com:here:mom:meta` and `@ns:com:here:mom:delta` namespaces that
were changed in MOM 10.
There were renamed and slightly reshaped, there are also some properties that are no longer valid in
MOM 10.

Because of the above, Naksha has to:

- **do nothing** if the handled (written or read) feature has version below `10.0.0` - we know that
  for "pre-MOM 10" features all of necessary namespaces are in place
- when writing `MOM 10` feature: create and shape missing namespace so that all logic that depends
  on them will work as expected
- when reading `MOM 10` feature: return them in the same shape as they were written (== don't
  include obsolete namespaces needed by Naksha)

### Checking `modelVersion`

[Mom10Verification](src/jvmMain/java/com/here/naksha/mom10/Mom10Verification.java) is responsible
for
checking whether given feature **has MOM version equal or greater to `10.0.0`**.
This information can be used to determine whether a further processing is required.

### Transforming from and to MOM 10

Transforming **from MOM 10** is assumed to happen only for writes - when a client comes with MOM 10
feature:

- we create outdated namespaces basing on data that is mappable from MOM 10
- we store the feature as it was given by the client with additional namespaces being stored next to
  the "new ones" (defined in MOM 10)
- the result is feature in "hybrid state" - containing both new and old namespaces

Transforming **to MOM 10** is assumed to happen only for reads - when a client want to retrieve
previously stored MOM 10 feature (described above):

- we fetch the whole "hybrid feature"
- we drop everything that we added in `from MOM 10` transformation (basically we delete obsolete
  namespaces)
- we return feature in the same shape as it was given to Naksha in the writing phase

The class responsible for these operations
is [Mom10Transformation](src/jvmMain/java/com/here/naksha/mom10/Mom10Transformation.java).

#### Populating old delta

| `@ns:com:here:mom:delta` properties supported in Naksha (pre MOM 10) | equivalents in `meta.moderationInfo` (MOM 10+) |
|----------------------------------------------------------------------|------------------------------------------------| 
| `originId`                                                           | `originId`                                     |
| `parentLink`                                                         | `parentLink`                                   |
| `changeState`                                                        | `changeState`                                  |
| `reviewState`                                                        | `reviewState`                                  |
| `streamId`                                                           | not applicable                                 |
| `potentialValue`                                                     | not applicable                                 |
| `priorityCategory`                                                   | not applicable                                 |
| `dueTS`                                                              | not applicable                                 |
| `changeCounter`                                                      | not applicable                                 |

Only the properties supported in both old delta NS and new `moderationInfo` will be used for
population of `@ns:com:here:mom:delta` namespace.

#### Populating old meta

Properties supported by both old `@ns:com:here:mom:meta` NS and MOM 10+ `meta` property

- `sourceId`
- `updatedByUser`
- `lastUpdatedBy`
- `modelVersion`
- `protectionFlags`
- `createdTS `
- `layerId`
- `updatedByApp`
- `lastUpdatedTS`

Only the properties listed above will be populated when creating `@ns:com:here:mom:meta` namespace.