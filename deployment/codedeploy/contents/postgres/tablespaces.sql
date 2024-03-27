-- it looks a little bit dummy, but we can not use any function/loop/block/transaction as
-- tablespace can't be executed within transaction.
-- REMEMBER to set transaction auto-commit in your tool where you execute this commands.

create tablespace naksha_${storageId}_main LOCATION '/tmp/${storageId}/main';

create tablespace naksha_${storageId}_head_0 LOCATION '/tmp/${storageId}/head0';
create tablespace naksha_${storageId}_head_1 LOCATION '/tmp/${storageId}/head1';
create tablespace naksha_${storageId}_head_2 LOCATION '/tmp/${storageId}/head2';
create tablespace naksha_${storageId}_head_3 LOCATION '/tmp/${storageId}/head3';
create tablespace naksha_${storageId}_head_4 LOCATION '/tmp/${storageId}/head4';
create tablespace naksha_${storageId}_head_5 LOCATION '/tmp/${storageId}/head5';
create tablespace naksha_${storageId}_head_6 LOCATION '/tmp/${storageId}/head6';
create tablespace naksha_${storageId}_head_7 LOCATION '/tmp/${storageId}/head7';

create tablespace naksha_${storageId}_hst_0 LOCATION '/tmp/${storageId}/hst0';
create tablespace naksha_${storageId}_hst_1 LOCATION '/tmp/${storageId}/hst1';
create tablespace naksha_${storageId}_hst_2 LOCATION '/tmp/${storageId}/hst2';
create tablespace naksha_${storageId}_hst_3 LOCATION '/tmp/${storageId}/hst3';
create tablespace naksha_${storageId}_hst_4 LOCATION '/tmp/${storageId}/hst4';
create tablespace naksha_${storageId}_hst_5 LOCATION '/tmp/${storageId}/hst5';
create tablespace naksha_${storageId}_hst_6 LOCATION '/tmp/${storageId}/hst6';
create tablespace naksha_${storageId}_hst_7 LOCATION '/tmp/${storageId}/hst7';