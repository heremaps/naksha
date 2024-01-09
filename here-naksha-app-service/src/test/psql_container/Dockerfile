# base image: minimal distro used just for pg_hint download
FROM --platform=linux/amd64 bitnami/minideb:bullseye

# download package with pg_hint_plan (https://github.com/ossc-db/pg_hint_plan/releases)
#ADD https://github.com/ossc-db/pg_hint_plan/releases/download/REL16_1_6_0/pg_hint_plan16-1.6.0-1.el8.x86_64.rpm .
ADD https://github.com/ossc-db/pg_hint_plan/releases/download/REL15_1_5_1/pg_hint_plan15-1.5.1-1.el8.x86_64.rpm .

# install rpm package (and install dependencies needed for that operation)
RUN apt-get update && \
    apt-get install -y --no-install-recommends alien rpm && \
    alien --to-deb ./pg_hint_plan*.rpm && \
    dpkg -i pg-hint-plan*.deb

# target image: Postgres with PostGIS extension (https://github.com/postgis/docker-postgis)
#FROM docker.io/postgis/postgis:16-3.4-alpine
FROM docker.io/postgis/postgis:15-3.4-alpine

# copy files prepared in base image
#COPY --from=0 /usr/pgsql-16/share/extension/ /usr/local/share/postgresql/extension
#COPY --from=0 /usr/pgsql-16/lib/pg_hint_plan.so  /usr/local/lib/postgresql
COPY --from=0 /usr/pgsql-15/share/extension/ /usr/local/share/postgresql/extension
COPY --from=0 /usr/pgsql-15/lib/pg_hint_plan.so  /usr/local/lib/postgresql
ENV  PGPASSWORD=postgres
CMD ["postgres","-c","shared_preload_libraries=pg_stat_statements"]
