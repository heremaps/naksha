ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG ARCH
ARG VERSION
FROM ${DR_NAKSHA_POSTGRES}:plv8-${ARCH}-${VERSION}

# GZIP extension (https://github.com/pramsey/pgsql-gzip/)
RUN cd /tmp/ \
    && git clone https://github.com/pramsey/pgsql-gzip.git \
    && cd pgsql-gzip \
    && git checkout v1.0.0 \
    && make \
    && make install \
# SELECT * FROM pg_available_extensions WHERE name = 'gzip';
#
# pg_hint_plan extension (https://github.com/ossc-db/pg_hint_plan)
    && cd /tmp/ \
    && git clone https://github.com/ossc-db/pg_hint_plan.git \
    && cd pg_hint_plan \
    && git checkout REL16_1_6_0 \
    && make \
    && make install \
# SELECT * FROM pg_available_extensions WHERE name = 'pg_hint_plan';
#
# pg_cron extension (https://github.com/citusdata/pg_cron)
    && cd /tmp/ \
    && git clone https://github.com/citusdata/pg_cron.git \
    && cd pg_cron \
    && git checkout v1.6.2 \
    && make \
    && make install \
# SELECT * FROM pg_available_extensions WHERE name = 'pg_cron';
#
# pg_partman (https://github.com/pgpartman/pg_partman)
    && cd /tmp/ \
    && git clone https://github.com/pgpartman/pg_partman.git \
    && cd pg_partman \
    && git checkout v5.0.1 \
    && make \
    && make install \
# SELECT * FROM pg_available_extensions WHERE name = 'pg_partman';
#
# Cleanup
    && rm -rf /tmp/*

EXPOSE 5432

CMD /usr/bin/run_postgres.sh
