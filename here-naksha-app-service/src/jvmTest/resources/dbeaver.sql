-- Select from admin_db
SET SESSION search_path TO naksha_admin_v3, "naksha~admin", topology, hint_plan, public;
SET enable_seqscan TO off;
select id, naksha_feature(feature, flags) as feature from "hub_internal:configs";
select id, naksha_feature(feature, flags) as feature from "hub_internal:event_handlers";
select id, naksha_feature(feature, flags) as feature from "hub_internal:extensions";
select id, naksha_feature(feature, flags) as feature from "hub_internal:spaces";
select id, naksha_feature(feature, flags) as feature from "hub_internal:storages";
select id, naksha_feature(feature, flags) as feature from "hub_internal:subscriptions";


-- Select from data_db
SET SESSION search_path TO test_map, "naksha~admin", topology, hint_plan, public;
SET enable_seqscan TO off;
select id, naksha_feature(feature, flags) as feature from "um-mod-dev:tc_280_auto_delete_on";
select id, naksha_feature(feature, flags) as feature from "um-mod-dev:tc_281";
