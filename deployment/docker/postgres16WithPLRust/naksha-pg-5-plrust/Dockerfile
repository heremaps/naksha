ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG VERSION
ARG TARGETPLATFORM
FROM ${DR_NAKSHA_POSTGRES}:pljava-${VERSION}

# https://github.com/tcdi/plrust
# https://plrust.io/install-plrust.html#trusted-installation-plus-cross-compilation
RUN dnf install clang \
    && chown postgres -R "$(pg_config --sharedir)/extension" \
    && chown postgres -R "$(pg_config --pkglibdir)"
USER postgres
RUN cd /tmp/ \
    && curl --tlsv1.3 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain stable -y \
    && source ~/.cargo/env \
    && git clone https://github.com/tcdi/plrust.git \
    && cd /tmp/plrust \
    && git checkout v1.2.8 \
# temporarily adjust dependencies version to support postgres 16 \
# https://github.com/tcdi/plrust/issues/417
# https://github.com/tcdi/plrust/issues/418
#    && sed -i '/^quote = "1"/a\
#       litemap = { version = "=0.7.4", forced = true } \n \
#       zerofrom = { version = "=0.1.5", forced = true } \n' plrust/Cargo.toml \
#    && sed -i '/^pub(crate) fn cargo_toml_template/a\
#               home = { version = "=0.5.9", forced = true } \n \
#               litemap = { version = "=0.7.4", forced = true } \n \
#               zerofrom = { version = "=0.1.5", forced = true } \n' plrust/src/user_crate/crating.rs \
    && rustup component add llvm-tools-preview rustc-dev \
    && if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
          rustup target install aarch64-unknown-linux-gnu; \
        elif [ "${TARGETPLATFORM}" = "linux/amd64" ]; then \
          rustup target install x86_64-unknown-linux-gnu; \
        fi \
# install pgrx
    && cargo install cargo-pgrx --locked --version=0.11.0 \
    && cargo pgrx init --pg16 "$(pg_config --sharedir)/pg_config" \
    && cd /tmp/plrust/plrustc \
# install plrust
    && ./build.sh \
    && mv /tmp/plrust/build/bin/plrustc ~/.cargo/bin/ \
# install postgrestd
    && cd /tmp/plrust/plrust \
#    && PG_VER=16 \
    && if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
                   STD_TARGETS="aarch64-postgres-linux-gnu" ./build; \
               elif [ "${TARGETPLATFORM}" = "linux/amd64" ]; then \
                   STD_TARGETS="x86_64-postgres-linux-gnu" ./build; \
               fi \
    && cargo pgrx install --release --features trusted -c /usr/bin/pg_config \
    && CONFIG_FILE="/etc/postgresql/16/main/postgresql.conf" \
    && if grep -q "^shared_preload_libraries" "$CONFIG_FILE"; then \
          grep "^shared_preload_libraries" "$CONFIG_FILE" | grep -q "plrust" || \
          sed -i "s/^\(shared_preload_libraries *= *'\(.*\)'\)/shared_preload_libraries = '\2, plrust'/" "$CONFIG_FILE"; \
        else \
          echo "shared_preload_libraries = 'plrust'" >> "$CONFIG_FILE"; \
        fi \
    && echo "plrust.work_dir = '/tmp'" >> "$CONFIG_FILE" \
    && chown root -R "$(pg_config --sharedir)/extension" \
    && chown root -R "$(pg_config --pkglibdir)" \
# Cleanup
    && rm -rf /tmp/*

USER root

EXPOSE 5432

CMD ["/usr/bin/run_postgres.sh"]