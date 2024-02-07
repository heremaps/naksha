#!/bin/bash
# Utility test script. Runs Http Storage tests. Do not merge to master
psql postgres -c "drop schema naksha_data_schema cascade"
gradle :here-naksha-app-service:test --tests 'com.here.naksha.app.service.ReadFeaturesByIdsHttpTest.*' --rerun-tasks
# gradle :here-naksha-app-service:test --tests 'com.here.naksha.app.service.ReadFeaturesByIdsHttpTest.*' --rerun-tasks 2> /dev/null | grep -e 'Hello' -e 'Bye' -e 'BUILD'
