ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG VERSION
ARG TARGETPLATFORM
FROM ${DR_NAKSHA_POSTGRES}:pljava-${VERSION}

# https://github.com/tcdi/plrust
# https://plrust.io/install-plrust.html#trusted-installation-plus-cross-compilation
RUN dnf install -y clang \
    && chown postgres -R "$(pg_config --sharedir)/extension" \
    && chown postgres -R "$(pg_config --pkglibdir)"
USER postgres
RUN cd /tmp/ \
# install rust via rustup
    && curl --tlsv1.3 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain stable -y \
    && source ~/.cargo/env \
# install PL Rust from source
    && git clone https://github.com/tcdi/plrust.git \
    && cd /tmp/plrust \
    && git checkout v1.2.8 \
# temporarily adjust dependencies version to support postgres 16 \
# https://github.com/tcdi/plrust/issues/417
# https://github.com/tcdi/plrust/issues/418
    && sed -i '/^omnipath = "0.1.6"/a\
       litemap = { version = "=0.7.4", forced = true } \n \
       zerofrom = { version = "=0.1.5", forced = true } \n' plrust/Cargo.toml \
    && sed -i '/^pgrx = { version = trusted_pgrx_version, package = "plrust-trusted-pgrx" }/a\
               home = { version = "=0.5.9", forced = true } \n \
               litemap = { version = "=0.7.4", forced = true } \n \
               zerofrom = { version = "=0.1.5", forced = true } \n' plrust/src/user_crate/crating.rs \
    && rustup default 1.72.0 \
    && rustup component add llvm-tools-preview rustc-dev \
    && if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
          rustup target install aarch64-unknown-linux-gnu; \
        elif [ "${TARGETPLATFORM}" = "linux/amd64" ]; then \
          rustup target install x86_64-unknown-linux-gnu; \
        fi \
    && cargo install cargo-pgrx --locked --version=0.11.0 \
    && cd /tmp/plrust/plrustc \
# install plrust
    && ./build.sh \
    && mv /tmp/plrust/build/bin/plrustc ~/.cargo/bin/ \
    && cargo pgrx init --pg16 "$(pg_config --bindir)/pg_config" \
# install postgrestd
    && cd /tmp/plrust/plrust \
    && if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
                   STD_TARGETS="aarch64-postgres-linux-gnu" ./build; \
               elif [ "${TARGETPLATFORM}" = "linux/amd64" ]; then \
                   STD_TARGETS="x86_64-postgres-linux-gnu" ./build; \
               fi \
    && cargo pgrx init --pg16 "$(pg_config --bindir)/pg_config" \
    && cargo pgrx install --release --features trusted
USER root
RUN chown root -R "$(pg_config --sharedir)/extension" \
    && chown root -R "$(pg_config --pkglibdir)" \
# Cleanup
    && rm -rf /tmp/*

EXPOSE 5432

CMD ["/usr/bin/run_postgres.sh"]