# TO-DO list for lib_data

Delete this file before merging to v3.

- [ ] ~~Remove uid~~
- [ ] ~~Custom members, indicies support~~
- [ ] ~~Split tn to (fn, version), renaming next_tn -> next_version~~
- [ ] ~~Removing of prev_tn~~
- [ ] Remove `Operation` - Alex
- [ ] Remove of deletion table - Alex 
- [ ] [**nice to have**] `PgIndex.kt` -clean up tuple-number leftovers. - Kacper 
- [ ] [**nice to have**] `PgIndex.kt` - replace the regex-based `next_version` stripping. - Kacper 
- [ ] [**nice to have**]` PgQueryWhereBuilder.kt` - `whereGuids()` iterates `tupleNumbers` twice; we can optimize it. - Kacper 
- [ ] [**nice to have**] Consider dropping the `flags` column. - Kacper 
- [ ] [**nice to have**]`nextTn` is still exposed externally. - Kacper 
- [ ] [**nice to have**] `MetaColumn.TUPLE_NUMBER -> PgColumn.fn` only orders by `fn`; extend to `(fn, version)` tuple. - Kacper 
- [ ] [**nice to have**] Generate change log of lib-psql (difference `lib_data` -> `v3`) - Kacper 
- [ ] [**required to merge**] Remove `flags`: column name `data_json` / `data_jbon` / `data_jbon_gzip` / `data_jsonb` (etc.)  - based on column name we can determine the type (we have already info about it in collection) - Kacper