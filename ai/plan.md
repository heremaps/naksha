# Workplan
- Remove `uid`


- Create a skill for the AI so it can create proxies and enumerations
  - We already have the concept, based upon JsEnum and Proxy
  - The JSON parser will return `PlatformObject` or `PlatformList`
  - We can add runtime types using `object.proxy(class)`
  - However, writing the proxies is a bit cumbersome
    - We need setter/getter and certain standard methods
    - `get{Name}()`, `set{Name}(value)`, `remove{Name}()`, `has{Name}()`
- Change the partitioning to use version and next_version as bigint
- Remove deletion table
  - Change algorithm that performs insert, update, delete, purge to no longer copy into deletion table
- update queries so they no longer access deletion table
  - they need to remove deleted objects, when not requested, using `AND ((version & 3) < 2)`
