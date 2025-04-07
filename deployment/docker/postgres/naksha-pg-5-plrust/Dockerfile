ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG VERSION
FROM ${DR_NAKSHA_POSTGRES}:pljava-${VERSION}

# https://github.com/tcdi/plrust
RUN cd /tmp/ \
    && curl --tlsv1.3 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain stable -y \
    && \
# Cleanup
    && rm -rf /tmp/*

EXPOSE 5432

CMD ["/usr/bin/run_postgres.sh"]