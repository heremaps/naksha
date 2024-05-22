ARG DR_NAKSHA_POSTGRES=hcr.data.here.com/naksha/postgres
ARG ARCH
ARG VERSION
FROM ${DR_NAKSHA_POSTGRES}:misc-${ARCH}-${VERSION}

# PL/Java (https://tada.github.io/pljava/)
# https://github.com/tada/pljava
# https://tada.github.io/pljava/install/install.html
# https://tada.github.io/pljava/use/policy.html
# https://www.percona.com/blog/postgresql-pl-java-how-to-part-1/
# https://www.slideshare.net/petereisentraut/postgresql-and-pljava
RUN yum install -y java-21-amazon-corretto maven \
    && cd /tmp/ \
    && git clone https://github.com/tada/pljava.git \
    && cd pljava \
    && git checkout V1_6_6 \
    && mvn clean install \
    && java -jar pljava-packaging/target/pljava-pg16.jar \
# SELECT * FROM pg_available_extensions WHERE name = 'pljava';
#
# Cleanup
    && rm -rf /tmp/*

EXPOSE 5432

CMD /usr/bin/run_postgres.sh
