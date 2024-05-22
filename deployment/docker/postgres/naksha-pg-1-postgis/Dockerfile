ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG ARCH
ARG VERSION
FROM ${DR_NAKSHA_POSTGRES}:base-${ARCH}-${VERSION}

#
# Install Geos (https://libgeos.org/usage/download/)
RUN yum install -y libxml2 libxml2-devel cmake \
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
    && rm /etc/ld.so.cache && ldconfig \
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
    && rm /etc/ld.so.cache && ldconfig \
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
# Test if library is available: ldconfig -v -N | grep jdal
# JSON-C
# This library is needed for some POSTGIS functions
# See: https://postgis.net/docs/postgis_installation.html#installation_configuration
# https://github.com/json-c/json-c
    && cd /tmp/ \
    && git clone https://github.com/json-c/json-c.git \
    && cd json-c \
    && git checkout json-c-0.17 \
    && mkdir json-c-build \
    && cd json-c-build \
    && cmake .. -DCMAKE_BUILD_TYPE=Release \
    && make \
    && make install \
    && rm /etc/ld.so.cache && ldconfig \
# Test if library is available: ldconfig -v -N | grep json-c
# PCRE
# See: https://postgis.net/docs/postgis_installation.html#installation_configuration
# https://github.com/PCRE2Project/pcre2 \
    && cd /tmp/ \
    && git clone https://github.com/PCRE2Project/pcre2.git \
    && cd pcre2 \
    && git checkout pcre2-10.43 \
    && mkdir pcre2-build \
    && cd pcre2-build \
    && cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DBUILD_STATIC_LIBS=OFF \
    && make \
    && make install \
    && rm /etc/ld.so.cache && ldconfig \
# Test if library is available: ldconfig -v -N | grep pcre2
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
    && make install \
# SELECT * FROM pg_available_extensions WHERE name = 'postgis';
# Cleanup
    && rm -rf /tmp/*

EXPOSE 5432

CMD /usr/bin/run_postgres.sh
