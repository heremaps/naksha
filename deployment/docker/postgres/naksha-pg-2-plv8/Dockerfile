ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG ARCH
ARG VERSION
FROM ${DR_NAKSHA_POSTGRES}:postgis-${ARCH}-${VERSION}

# PLV8 extension (https://github.com/plv8/plv8.git)
# Note: Very slow, requires one hour!
RUN cd /tmp/ \
    && git clone https://github.com/plv8/plv8.git \
    && cd plv8 \
    && git checkout v3.2.2 \
    && make \
    && make install \
# SELECT * FROM pg_available_extensions WHERE name = 'plv8';
#
# Cleanup
    && rm -rf /tmp/*

EXPOSE 5432

CMD /usr/bin/run_postgres.sh
