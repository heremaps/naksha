ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG VERSION
ARG TARGETPLATFORM
FROM ${DR_NAKSHA_POSTGRES}:pljava-${VERSION}

# https://github.com/tcdi/plrust
# https://plrust.io/install-plrust.html#trusted-installation-plus-cross-compilation
RUN chown postgres -R /usr/share/postgresql/16/extension/ \
    && chown postgres -R /usr/lib/postgresql/15/lib/ \
USER postgres
RUN cd /tmp/ \
    && curl --tlsv1.3 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain stable -y \
    && git clone https://github.com/tcdi/plrust.git \
    && rustup default 1.86.0 \
# install pgrx
    && cargo install cargo-pgrx --locked \
    && cargo pgrx init --pg16 /usr/local/bin/pg_config \
    && rustup component add llvm-tools-preview rustc-dev \
    && if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
               rustup target install aarch64-unknown-linux-gnu; \
           elif [ "${TARGETPLATFORM}" = "linux/amd64" ]; then \
               rustup target install x86_64-unknown-linux-gnu; \
           fi \
    && cd ~/plrust/plrustc \
# install plrust
    && ./build.sh \
    && mv ~/plrust/build/bin/plrustc ~/.cargo/bin/ \
# install postgrestd
    && cd ~/plrust/plrust \
    && PG_VER=16 \
    && if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
                   STD_TARGETS="aarch64-postgres-linux-gnu "; \
               elif [ "${TARGETPLATFORM}" = "linux/amd64" ]; then \
                   STD_TARGETS="x86_64-postgres-linux-gnu "; \
               fi \
    && ./build \
    && cargo pgrx install --release --features trusted -c /usr/bin/pg_config \
    && CONFIG_FILE="/etc/postgresql/16/main/postgresql.conf" \
    && if grep -q "^shared_preload_libraries" "$CONFIG_FILE"; then \
          grep "^shared_preload_libraries" "$CONFIG_FILE" | grep -q "plrust" || \
          sed -i "s/^\(shared_preload_libraries *= *'\(.*\)'\)/shared_preload_libraries = '\2, plrust'/" "$CONFIG_FILE"; \
        else \
          echo "shared_preload_libraries = 'plrust'" >> "$CONFIG_FILE"; \
        fi \
    && echo "plrust.work_dir = '/tmp'" >> "$CONFIG_FILE" \
# Cleanup
    && rm -rf /tmp/*

EXPOSE 5432

CMD ["/usr/bin/run_postgres.sh"]