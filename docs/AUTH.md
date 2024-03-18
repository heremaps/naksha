# Authorization

Naksha supports various out-of-box authorization checks while performing read/write operations against following resources:

- [Storage](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/naksha/Storage.java)
- [EventHandler](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/naksha/EventHandler.java)
- [Space](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/naksha/Space.java)
- [XyzFeature](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/geojson/implementation/XyzFeature.java)
- [XyzCollection](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/models/naksha/XyzCollection.java)

based on User's access profile supplied using following attributes as part of [NakshaContext](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/NakshaContext.java):

- **appId** - Mandatory
  - The unique application identifier, requesting current operation.
- **author** - Optional
  - The unique identifier of a logged-in user, who is requesting current operation.
- **urm** - Optional (but Mandatory for meaningful authorization checks)
  - The user-rights-matrix, defines resource level access authorized for current operation. 
- **su** - Optional
  - Superuser flag, if set, all authorization checks will be bypassed. This is useful where one level of authorization is already performed and we like to avoid repetitive checks on recursive/internal calls.

## 1. URM Concept

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



### Attribute Map

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


### JWT expectations

Naksha REST App accepts [JWT token](https://datatracker.ietf.org/doc/html/rfc7519) as part of:

* Header `Authorization` = `Bearer <jwt-token>`, OR
* Query parameter `access_token` = `<jwt-token>`

The [JWT token](https://datatracker.ietf.org/doc/html/rfc7519) must be digitally signed by a trusted partner using its private key (`RS256` encryption algorithm),
and the public key of this trusted partner must be added into the Naksha configuration so that the service can validate the token's authenticity.

JWT format follows:

```text
  base64UrlEncoded(header)
+ "."
+ base64UrlEncoded(payload)
+ "."
  signature
```

The sample encoded JWT will look like (can be viewed on [jwt.io](https://jwt.io)):

```text
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhcHBJZCI6IndlYi1jbGllbnQtYXBwLWlkIiwiYXV0aG9yIjoibXktdXNlci1pZCIsInVybSI6eyJ4eXotaHViIjp7InJlYWRGZWF0dXJlcyI6W3sic3RvcmFnZUlkIjoiZGV2LSoifV19fSwiaWF0IjoxNzA0MDYzNTk5LCJleHAiOjE3MDQwNjcxOTl9.g7maKIpDQ6d8MoC7lPQDa_6BLKV5HhpN9t1BkcdFmNSetc-dHIcor_mhvc4GNpJELEMCBiTiF8RdlY_PEOooJc4Ixx5yWFoeIEaKv-aunvf6TZsOlD8F5KX8CmL8QzEO7t8YrSVz-F3WYrw1rmnl_1WC2tscMUBvfFHRifq3h7F46ZMswO6fm8AGHW0bbSeDCwK2VcjkYOwGVYWmSPodtxT7ie8uxJlAFxaCGzxV1WkVnrqIZFdPcnq3hM_FjbSw01MxOD3qdiL47HRXQnvOzsKjhi5ihClihwiua4N9xOeq2I8nX5_2YJIRWjS8pAozRp7cfnhb15Sm8JevqEwz1A
```

JWT payload is expected to have custom claims `appId`, `author` and `urm`, which is then populated into [NakshaContext](../here-naksha-lib-core/src/main/java/com/here/naksha/lib/core/NakshaContext.java) whenever REST API request is picked up for processing.

```json
{
    "appId": "web-client-app-id",
    "author": "my-user-id",
    "urm": {
        "xyz-hub": {
            "readFeatures": [
                {
                    "storageId": "dev-*"
                }
            ]
        }
    },
    "iat": 1704063599,
    "exp": 1704067199
}
```

### Auth Modes

Service can be executed in two modes based on [AuthorizationType](../here-naksha-app-service/src/main/java/com/here/naksha/app/service/http/auth/Authorization.java) specified as part of startup [config](../here-naksha-lib-hub/src/main/java/com/here/naksha/lib/hub/NakshaHubConfig.java):

* `DUMMY`
  - Dummy mode
  - useful, when we want to run service in local / test / dev environment, where security is not that important
  - it will use internally generated super-user URM as part of NakshaContext to allow full access to all resources
* `JWT`
  - Real JWT mode
  - useful, for cloud / prod environment, where security is MUST
  - it will validate the JWT as part of each REST API request and extract the URM for further authorization checks
  - Absent of JWT will result into Http error code 401 - Unauthorized




---

## 3. Supported Actions and Attributes

**TODO**

Table of all supported **Actions** and **Attributes** for validating authorization against individual resource operation:

**NOTE** for **Attributes**:
  * For all, **exact** String value comparison is supported by default.
  * For some, **wild-card** value (e.g. `storage-dev*`) is also supported (explicitly marked in table)
  * For all, **List** of values is supported by default.

**NOTE** for **Actions**:
  * **Limited View** - means, read of resource is allowed BUT without exposing `properies` object (so typically one can read `id`, `title`, `description` etc but not `properties` object)

### 3.1 Storage

#### Attributes

* `id` - wild-card supported
* `tags` - wild-card supported - prop path `properties.@ns:com:here:xyz.tags`
* `appId` - `properties.@ns:com:here:xyz.appId`
* `author` - `properties.@ns:com:here:xyz.author`
* `className` - `className`
* **Space** related:
  * `spaceId` - wild-card supported

#### Actions

| Action        | Allowed operations                                                                     | Remarks |
|---------------|----------------------------------------------------------------------------------------|---------|
| `useStorages` | Get Storage Implementation for a given storageId (and optionally spaceId, if supplied) |         |
| `useStorages` | Limited view - ReadFeatures from virtual space (`naksha:storages`)                     |         |
| `manageStorages` | Full control. Read/Write Features from/to virtual space (`naksha:storages`).        |         |


### 3.2 Event Handler

#### Attributes

#### Actions

| Action | Allowed operations | Remarks |
|--------|--------------------|---------|
|        |                    |         |
|        |                    |         |
|        |                    |         |


### 3.3 Space

#### Attributes

#### Actions

| Action | Allowed operations | Remarks |
|--------|--------------------|---------|
|        |                    |         |
|        |                    |         |
|        |                    |         |


### 3.4 XyzFeature

#### Attributes

#### Actions

| Action | Allowed operations | Remarks |
|--------|--------------------|---------|
|        |                    |         |
|        |                    |         |
|        |                    |         |


### 3.5 XyzCollection

#### Attributes

#### Actions

| Action | Allowed operations | Remarks |
|--------|--------------------|---------|
|        |                    |         |
|        |                    |         |
|        |                    |         |




