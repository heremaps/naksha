ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG ARCH
ARG VERSION
FROM ${DR_NAKSHA_POSTGRES}:pljava-${ARCH}-${VERSION}

ADD files /

RUN chown postgres:postgres /usr/bin/run_postgres.sh \
    && chmod 755 /usr/bin/run_postgres.sh \
    && chown -R postgres:postgres /home/postgres \
    && cd /home/postgres \
    && chown -R postgres:postgres *

EXPOSE 5432

CMD /usr/bin/run_postgres.sh
