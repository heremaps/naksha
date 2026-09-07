FROM public.ecr.aws/amazonlinux/amazonlinux:latest

# Execute all commands at ones, so we only get one layer

# Install PostgresQL (https://www.postgresql.org/ftp/source/)
RUN dnf update -y
RUN dnf install -y bison-devel readline-devel zlib-devel openssl-devel wget vim
RUN dnf install -y mlocate icu libicu-devel e2fsprogs e2fsprogs-devel lz4 lz4-devel uuid uuid-devel sudo
RUN dnf install -y --allowerasing curl curl-devel
RUN dnf -y groupinstall "Development Tools"
RUN cd /tmp/ \
    && wget https://ftp.postgresql.org/pub/source/v16.8/postgresql-16.8.tar.gz \
    && tar xzf postgresql-16.8.tar.gz \
    && cd postgresql-16.8 \
    && ./configure --prefix=/usr/local --with-blocksize=32 --with-wal-blocksize=32 --with-lz4 --with-zstd --with-openssl --with-uuid=ossp \
    && make world WITH_PGBLOCKSIZE=32K \
    && groupadd postgres -g 5430 \
    && useradd postgres -u 5432 -g 5430 \
    && make install-world \
    && updatedb \
# Cleanup
    && rm -rf /tmp/*

EXPOSE 5432

CMD ["/usr/bin/run_postgres.sh"]
