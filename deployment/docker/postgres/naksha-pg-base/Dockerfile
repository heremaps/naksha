FROM public.ecr.aws/amazonlinux/amazonlinux:latest

# Execute all commands at ones, so we only get one layer

# Install PostgresQL (https://www.postgresql.org/ftp/source/)
RUN yum update -y  \
    && yum install -y bison-devel readline-devel zlib-devel openssl-devel wget vim \
    && yum install -y mlocate icu libicu-devel e2fsprogs e2fsprogs-devel lz4 lz4-devel uuid uuid-devel sudo \
    && yum install -y --allowerasing curl curl-devel \
    && yum -y groupinstall "Development Tools" \
    && cd /tmp/ \
    && wget https://ftp.postgresql.org/pub/source/v16.2/postgresql-16.2.tar.gz \
    && tar xzf postgresql-16.2.tar.gz \
    && cd postgresql-16.2 \
    && ./configure --prefix=/usr/local --with-blocksize=32 --with-wal-blocksize=32 --with-lz4 --with-zstd --with-openssl --with-uuid=ossp \
    && make world WITH_PGBLOCKSIZE=32K \
    && groupadd postgres -g 5430 \
    && useradd postgres -u 5432 -g 5430 \
    && make install-world \
    && updatedb \
#
# Install Geos (https://libgeos.org/usage/download/)
    && yum install -y libxml2 libxml2-devel cmake \
    && cd /tmp/ \
    && wget https://download.osgeo.org/geos/geos-3.12.1.tar.bz2 \
    && tar xjf geos-3.12.1.tar.bz2 \
    && cd geos-3.12.1 \
    && mkdir _build \
    && cd _build \
    && cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/usr/local .. \
    && make \
    && make install \
    && echo "/usr/local/lib64" > /etc/ld.so.conf.d/usr_local_lib64.conf \
    && rm /etc/ld.so.cache \
    && ldconfig \
# Test if library is available: ldconfig -v -N | grep geos
#
# Proj4 (https://proj.org/en/9.4/development/index.html)
# https://github.com/OSGeo/PROJ
    && yum install -y sqlite sqlite-devel libtiff libtiff-devel \
    && cd /tmp/ \
    && git clone https://github.com/OSGeo/PROJ.git \
    && cd PROJ \
    && mkdir build \
    && cd build \
    && cmake .. \
    && cmake --build . \
    && cmake --build . --target install \
#
# Install GDal (https://gdal.org/index.html)
# https://github.com/OSGeo/gdal
    && yum install -y python3-devel python3-setuptools \
    && cd /tmp/ \
    && git clone https://github.com/OSGeo/gdal.git \
    && cd gdal \
    && git checkout v3.8.4 \
    && mkdir build \
    && cd build \
    && cmake -DCMAKE_BUILD_TYPE=Release .. \
    && cmake --build . \
    && cmake --build . --target install \
#
# PostGis extension
# https://postgis.net/docs/postgis_installation.html
# https://git.osgeo.org/gitea/postgis/postgis
    && yum install -y protobuf protobuf-devel protobuf-c-devel libxslt libxslt-devel autoconf autoconf-archive perl-Time-HiRes libpq libpq-devel \
    && cd /tmp/ \
    && git clone https://git.osgeo.org/gitea/postgis/postgis.git \
    && cd postgis \
    && git checkout 3.4.2 \
    && ./autogen.sh \
    && ./configure \
    && make \
    && export PGUSER=postgres \
#   && make check \
    && make install \
# SELECT * FROM pg_available_extensions WHERE name = 'postgis';
#
# GZIP extension (https://github.com/pramsey/pgsql-gzip/)
    && cd /tmp/ \
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
