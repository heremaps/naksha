# Authorization

**TODO** - Add table of content

Naksha supports various out-of-box authorization checks while performing read/write operations against following resources:

- [Storage](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/naksha/Storage.java)
- [EventHandler](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/naksha/EventHandler.java)
- [Space](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/naksha/Space.java)
- [Feature](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/geojson/implementation/XyzFeature.java)
- [Collection](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/naksha/XyzCollection.java)

based on User's access profile supplied using following attributes as part of [NakshaContext](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/NakshaContext.java):

- **appId** - Mandatory
  - The unique application identifier, requesting current operation.
- **author** - Optional
  - The unique identifier of a logged-in user, who is requesting current operation.
- **urm** - Optional (but Mandatory for meaningful authorization checks)
  - The user-rights-matrix, defines resource level access authorized for current operation. 
- **su** - Optional
  - Superuser flag, if set, all authorization checks will be bypassed. This is useful where one level of authorization is already performed and we like to avoid repetitive checks on recursive/internal calls.

## 1. URM Format, Sample

URM (User-Rights-Matrix) follows the Map<String, Object> format as below,
which allows to define a matrix of an **Action** against target **Resource** by specifying one/more **AttributeMaps** of zero/more **Attributes**, that should be compared to validate request authorization:

```json
{
    "urm": {
        "xyz-hub": {
            // zero or more Actions
            "<action>": [
                // one or more Attribute-Map's (atleast one should match)
                {
                    // zero or more access Attributes (all should match)
                    "<accessAttribute1>": "<exactValue>",
                    "<accessAttribute2>": "<valueWithWildcard*>",
                    "<accessAttribute3>": [  // one or more values (all should match)
                        "<anotherExactValue>",
                        "<anotherValueWithWildcard*>"
                    ]
                }
            ]
        }
    }
}
```

For example:

```json
{
    "urm": {
        "xyz-hub": {
            // zero or more Actions
            "readFeatures": [
                // one or more Attribute-Map's (atleast one should match)
                {
                    // zero or more access Attributes (all should match)
                    "id": "my-unique-feature-id",
                    "storageId": "id-with-wild-card-*",
                    "tags": [  // one or more values (all should match)
                        "my-unique-tag",
                        "some-common-tag-with-wild-card-*"
                    ]
                }
            ]
        }
    }
}
```



### Action

Defines type of operation allowed against a resource. Refer later sections on this page, to know which specific actions are allowed against which resource.

But, in general:

- **useXXX**: Means that a resource can be used and **id**, **title**, **description** can be viewed, but NOT **properties** details
- **manageXXX**: Means that a resource can be read and modified (i.e. full access).
- **createXXX**: Allow the creation of a resource.
- **readXXX**: Allow reading of a resource.
- **updateXXX**: Allow to update a resource.
- **deleteXXX**: Allow to delete a resource.

Each Action can have list of one or more **AttributeMaps**, which is then compared against target Resource AttributeMaps to validate authorization.

For example:

```json
{
    "readFeatures": [
        // one or more Attribute-Map's (atleast one should match)
        {
            "id": "my-unique-feature-id"
        },
        {
            "storageId": "id-with-wild-card-*"
        },
        {
            "tags": [
                "my-unique-tag",
                "some-common-tag-with-wild-card-*"
            ]
        }
    ]
}
```

Thumb rule is - **Atleast One** access AttributeMap should match with resource AttributeMap, to allow access for that Action.

So:

* If Action has no attribute maps - Access is NOT allowed.
* If Action has one or more attribute maps - Atleast one of the AttributeMaps should match



### Attribute Maps

Every AttributeMap can have zero or more **Access Attributes**.

For example:

```json
{
    "readFeatures": [
        {
            // empty attribute map (is a MATCH)
        },
        {
            // one or more attributes (all should match)
            "id": "my-unique-feature-id",
            "storageId": "id-with-wild-card-*",
            "tags": [
                "my-unique-tag",
                "some-common-tag-with-wild-card-*"
            ]
        }
    ]
}
```

Thumb rule is - **All specified Access Attributes** should match with resource attributes, to call an AttributeMap to have a MATCH.

So:

* If AttributeMap has zero/no attributes - Map is considered as a MATCH.
* If AttributeMap has one or more attributes - Then all attributes should match.



### Access Attribute

Every Access Attribute can be a **Scalar** or a **List** of Scalar, where Scalar can be exact value or (in some cases) value with a wild card (*).

For example:


```json
{
    "id": "my-unique-feature-id",       // exact value match
    "storageId": "id-with-wild-card-*", // startsWith match
    "tags": [                           // all should match
        "my-unique-tag",
        "some-common-tag-with-wild-card-*"  // startsWith match in list
    ]
}
```

Thumb rule is - **All specified values** should match with resource attribute values, to call an Access Attribute a MATCH.

So:

* If AccessAttribute has an exact Scalar value - The value should match with resource attribute.
* If AccessAttribute has a wild-card Scalar value - The prefix portion of the value should match with resource attribute.
* If AccessAttribute has a List of Scalar value - Each value should match (as Scalar comparison).





---

## 2. REST API Authorization

**TODO**

### Header expectations

JWT format (header, payload, signature)

JWT payload sample

```json
{
  "appId": "some-unique-client-app-id",
  "author": "some-unique-user-id",
  "urm": {
    "xyz-hub": {
      "<action>": [
        {
          "<accessAttribute>": "<attributeValue>"
        }
      ]
    }
  }
}
```

### JWT Validation

pvt/pub key, expiry




---

## 3. Supported Actions and Attributes

**TODO**

Table of all actions and attributes for each resource:

- Storage
- EventHandler
- Space
- Feature
- Collection


