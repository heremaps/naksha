# syntax=docker/dockerfile:1
ARG NAKSHA_DIR="/naksha"
ARG NAKSHA_CLI_DIR="${NAKSHA_DIR}/here-naksha-cli"
ARG JAVA_HOME="/usr/lib/jvm/java-23"

###############
# BUILD STAGE #
###############
FROM public.ecr.aws/amazonlinux/amazonlinux:2023.8.20250808.1-minimal AS build

ARG GRADLE_VERSION="gradle-8.14.3"
RUN <<EOF
dnf -y update
# install utils
dnf install -y wget unzip findutils git
# install java
dnf install -y java-23-amazon-corretto-devel java-11-amazon-corretto-devel
dnf clean all
# install gradle
cd /tmp
wget https://services.gradle.org/distributions/${GRADLE_VERSION}-bin.zip
mkdir /opt/gradle
unzip -d /opt/gradle ${GRADLE_VERSION}-bin.zip
rm -rf /tmp/*
EOF
ENV GRADLE_HOME="/opt/gradle/${GRADLE_VERSION}"
ENV PATH="${GRADLE_HOME}/bin:${PATH}"
# fetch and build cli
ARG COMMIT_SHA="d11241e1599cb3746b00139b2164ca8bc25324a6" NAKSHA_DIR
WORKDIR ${NAKSHA_DIR}
RUN <<EOF
# do it this way to fetch only specified commit
git init
git remote add origin https://github.com/heremaps/naksha
git fetch origin ${COMMIT_SHA}
git reset --hard FETCH_HEAD
gradle :here-naksha-cli:jvmJar
EOF

###############
# FINAL STAGE #
###############
FROM ghcr.io/naksha-oss/naksha-postgres:v16.2-r5

ARG JAVA_HOME NAKSHA_CLI_DIR CLI_FILES_PATH="/tmp/naksha-cli-files"
ARG srcStorageConfig="${CLI_FILES_PATH}/srcStorageConfig.json" srcMapId="" srcCollectionId=""
ARG targetStorageConfig="${CLI_FILES_PATH}/targetStorageConfig.json" targetMapId="genmap" targetCollectionId="gencol"
ENV PGPASSWORD="password"
COPY ./naksha-cli-files ${CLI_FILES_PATH}
WORKDIR /tmp
# mount temporarily java and jars from the build stage only for this run
RUN \
--mount=from=build,source=${JAVA_HOME},target=/java \
--mount=from=build,source=${NAKSHA_CLI_DIR}/build/libs,target=/cli \
<<EOF
# run postgres in new bash and in the background to avoid process replacement and build process freeze
echo "Starting postgres..."
bash -c "/usr/bin/run_postgres.sh" >> std.out 2>&1 &
echo "Waiting for postgres..."
timeout 60 bash -c 'until [ "$(grep -c "Future log output will appear" std.out)" -eq 2 ]; do sleep 1; done'
timeout 60 bash -c 'until printf "" 2>>/dev/null >>/dev/tcp/$0/$1; do sleep 1; done' localhost 5432
echo "Executing CLI..."
JAR=$(find /cli -name "naksha-cli*.jar" -type f -exec ls -t {} + | head -n 1)
/java/bin/java -jar ${JAR} copy \
--srcStorageConfig ${srcStorageConfig} \
--srcMapId "${srcMapId}" \
--srcCollectionId "${srcCollectionId}" \
--targetStorageConfig ${targetStorageConfig} \
--targetMapId "${targetMapId}" \
--targetCollectionId "${targetCollectionId}" \
--autoCreateTarget || exit $?
echo "Stoping postgres..."
su postgres -c "pg_ctl -D /usr/local/pgsql/data stop"
rm -rf /tmp/*
EOF