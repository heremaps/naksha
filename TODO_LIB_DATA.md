# TO-DO list for lib_data

Delete this file before merging to v3.

- [ ] ~~Remove uid~~
- [ ] ~~Custom members, indicies support~~
- [ ] ~~Split tn to (fn, version), renaming next_tn -> next_version~~
- [ ] ~~Removing of prev_tn~~
- [ ] ~~Remove `Operation`~~
- [ ] ~~Remove of deletion table~~ 
- [ ] ~~[**nice to have**] `PgIndex.kt` -clean up tuple-number leftovers. - Kacper~~
- [ ] ~~[**nice to have**] `PgIndex.kt` - replace the regex-based `next_version` stripping. - Kacper~~
- [ ] ~~[**nice to have**]` PgQueryWhereBuilder.kt` - `whereGuids()` iterates `tupleNumbers` twice; we can optimize it. - Kacper~~
- [ ] ~~[**nice to have**] Consider dropping the `flags` column. - Kacper~~ 
- [ ] [**nice to have**]`nextTn` is still exposed externally. - Kacper 
- [ ] [**nice to have**] `MetaColumn.TUPLE_NUMBER -> PgColumn.fn` only orders by `fn`; extend to `(fn, version)` tuple. - Kacper 
- [ ] [**nice to have**] Generate change log of lib-psql (difference `lib_data` -> `v3`) - Kacper 
- [ ] ~~[**required to merge**] Remove `flags`: column name `data_json` / `data_jbon` / `data_jbon_gzip` / `data_jsonb` (etc.)  - based on column name we can determine the type (we have already info about it in collection) - Kacper~~
- [ ] [**required to merge**] Review:
    - The minimal columns should be: (`fn`, `version`), `next_version` _(HISTORY only)_, `feature`, `global_book_fn` _(bigint)_, `id` _(potentially `null`)_, … configurable
    - Ensure that when `fn` is negative, `id` must be set
    - Ensure that when `fn` is positive, `id` must NOT be set
    - Ensure that the index above `id` is _(in HEAD)_ unique, but conditional, so only `WHERE id IS NOT NULL`
- [ ] ~~[**required to merge**]  Ensure that `tags` are always stored as raw `jsonb` _(indexable)_ - Kacper~~
- [ ] [**required to merge**] Upgrade JBON1 to JBON2 - Alex
- [ ] [**required to merge**]  Improve `naksha_feature` to autodetect:
    - is gzipped, then unpack
    - is jbon2, then decode
    - is json, then cast
- [ ] [**required to merge**] We allow in collection to configure:
    - a) If the feature is compressed, and which compression method to use _(`gzip`, `lz4`)
        - This requires to add a header detection, and this requires JBON2
    - b) The feature can be stored as JSON or JBON
    - c) Default is `gzip,jsonb`  