ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG VERSION
ARG TARGETPLATFORM
FROM ${DR_NAKSHA_POSTGRES}:pljava-${VERSION}


# TOO BADDDD!!!!!!!!!!!!!! PL RUST DOES NOT SUPPORT POSTGRESQL 17 YET!!!!!!!!!!!!!!!!!




# https://github.com/tcdi/plrust
# https://plrust.io/install-plrust.html#trusted-installation-plus-cross-compilation
RUN curl --tlsv1.3 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain stable -y \
    && rustup component add llvm-tools-preview rustc-dev \
    && if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
               rustup target install aarch64-unknown-linux-gnu; \
           elif [ "${TARGETPLATFORM}" = "linux/amd64" ]; then \
               rustup target install x86_64-unknown-linux-gnu; \
           fi \
    && cd ~/plrust/plrustc \
    && ./build.sh \
    && mv ~/plrust/build/bin/plrustc ~/.cargo/bin/ \
# install postgrestd
    && cd ~/plrust/plrust \
    && PG_VER=17 \
    && if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
                   STD_TARGETS="aarch64-postgres-linux-gnu "; \
               elif [ "${TARGETPLATFORM}" = "linux/amd64" ]; then \
                   STD_TARGETS="x86_64-postgres-linux-gnu "; \
               fi \
    && ./build \
    && cargo pgrx install --release --features trusted -c /usr/bin/pg_config

EXPOSE 5432

CMD ["/usr/bin/run_postgres.sh"]