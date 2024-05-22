# Naksha PostgresQL
This folder contains docker configurations needed to build Naksha-Hub docker and the PostgresQL database docker that is used by Naksha-Hub.

## Build the image

### Prepare
Before you can start building the image, install docker, for example [Docker Desktop](https://docs.docker.com/desktop/install/).

The first step is to export environment variables, and then login to your docker registry (**DR**), example of HERE registry:

```bash
export DR_USER='<here-user>'
export DR_PWD='<encrypted-password>'
export DR_HOST="hcr.data.here.com"
export DR_NAKSHA_POSTGRES="$DR_HOST/naksha/postgres"
docker login -u="$DR_USER" -p="$DR_PWD" $DR_HOST
```

**Note**: If you feel saver, enter the password on CLI. The rest of the instructions are no longer environment dependent.

The PostgresQL docker will be build in multiple steps:

- Step 0: Build the [PostgresSQ database itself](./naksha-pg-0-base/)
- Step 1: Build the [PosgtGIS extension](./naksha-pg-1-postgis)
- Step 2: Build the [PLV8 extension](./naksha-pg-2-plv8)
- Step 3: Build other [miscellaneous extensions](./naksha-pg-3-misc) like GZIP
- Step 4: Build [PL/Java extension](./naksha-pg-4-pljava), which is experimental for now
- Step 5: Build the [release](./naksha-pg-release) that contains configuration and start script, we do it as a standalone step, because this allows easily to change the configuration of the PostgresQL database without the need to compile anything again.

### Build
The Naksha PostgresQL image is build in steps, follow these instructions:

```bash
# Define postgres version, and revision to be build
# v{pg-major}.{pg-minor}[.{pg-revision}]-r{revision}
export BASE_VER="v16.2-r3"
export POSTGIS_VER="v16.2-r3"
export PLV8_VER="v16.2-r3"
export MISC_VER="v16.2-r3"
export PLJAVA_VER="v16.2-r3"
export RELEASE_VER="v16.2-r3"
```

Ones done, start compiling

```bash
cd deployment/docker/postgres

# Build base image and push
cd naksha-pg-0-base
docker build --platform=linux/arm64 --push -t "$DR_NAKSHA_POSTGRES:base-arm64-${BASE_VER}" .
docker build --platform=linux/amd64 --push -t "$DR_NAKSHA_POSTGRES:base-amd64-${BASE_VER}" .
cd ..

# Build postgis image and push
cd naksha-pg-1-postgis
docker build --platform=linux/arm64 \
       --build-arg="ARCH=arm64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${BASE_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:postgis-arm64-${POSTGIS_VER}" .
docker build --platform=linux/amd64 \
       --build-arg="ARCH=amd64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${BASE_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:postgis-amd64-${POSTGIS_VER}" .
cd ..

# Build plv8 image and push
cd naksha-pg-2-plv8
docker build --platform=linux/arm64 \
       --build-arg="ARCH=arm64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${POSTGIS_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:plv8-arm64-${PLV8_VER}" .
docker build --platform=linux/amd64 \
       --build-arg="ARCH=amd64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${POSTGIS_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:plv8-amd64-${PLV8_VER}" .
cd ..

# Build miscellaneous image and push
cd naksha-pg-3-misc
docker build --platform=linux/arm64 \
       --build-arg="ARCH=arm64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${PLV8_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:misc-arm64-${MISC_VER}" .
docker build --platform=linux/amd64 \
       --build-arg="ARCH=amd64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${PLV8_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:misc-amd64-${MISC_VER}" .
cd ..

# Build pljava image and push
cd naksha-pg-4-pljava
docker build --platform=linux/arm64 \
       --build-arg="ARCH=arm64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${MISC_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:pljava-arm64-${PLJAVA_VER}" .
docker build --platform=linux/amd64 \
       --build-arg="ARCH=amd64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${MISC_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:pljava-amd64-${PLJAVA_VER}" .
cd ..

# Build the final postgres with run-scripts and default configurations
# Note, this can be done multiple times without any need to re-build the previous images
# Therefore we introduce the BASE var
cd naksha-pg-release
docker build --platform=linux/arm64 \
       --build-arg="ARCH=arm64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${PLJAVA_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:arm64-${RELEASE_VER}" \
       -t "${DR_NAKSHA_POSTGRES}:arm64-latest" .
docker build --platform=linux/amd64 \
       --build-arg="ARCH=amd64" \
       --build-arg="DR_NAKSHA_POSTGRES=$DR_NAKSHA_POSTGRES" \
       --build-arg="VERSION=${PLJAVA_VER}" \
       --push \
       -t "${DR_NAKSHA_POSTGRES}:amd64-${RELEASE_VER}" \
       -t "${DR_NAKSHA_POSTGRES}:amd64-latest" .
cd ..
```

**Notes**:
- It is totally valid and expected that you only have one revision for the **base** image and the **plv8** image, but multiple revisions for the release.
- To show more output, add `--progress=plain` argument to the `docker build` command.

## Build from scratch
You can execute every single step from the **Dockerfile** manually, to do this, start a blank container and then copy and paste the lines from the **Dockerfile**:

```bash
# base
docker run --name naksha_pg_build -it --entrypoint bash public.ecr.aws/amazonlinux/amazonlinux:latest
# postgis
docker run --name naksha_pg_build -it --entrypoint bash ${DR_NAKSHA_POSTGRES}:base-arm64-${BASE_VER}
docker run --name naksha_pg_build --platform=linux/amd64 -it --entrypoint bash ${DR_NAKSHA_POSTGRES}:base-amd64-${BASE_VER}

```

## Modify the build
Building takes a long time and is then directly pushed to the remote server. This is very unhandy while modifying the build. Therefore, it is recommended to first test the modification in a local docker container before updating the build script.

```bash
# Declare a new version
export BASE="v16.2-r0"
export VERSION="v16.2-r2"
# Build the docker
docker build --platform=linux/arm64 \
       --build-arg="ARCH=arm64" \
       --build-arg="DR_NAKSHA_POSTGRES=${DR_NAKSHA_POSTGRES}" \
       --build-arg="VERSION=$BASE" \
       -t "${DR_NAKSHA_POSTGRES}:arm64-${VERSION}" \
       .
# Run the docker
mkdir -p ~/pg_data
mkdir -p ~/pg_temp
# If the database should be cleared, do
# rm -rf ~/pg_data/*
# rm -rf ~/pg_temp/*
docker run --name naksha_pg \
       -v ~/pg_data:/usr/local/pgsql/data \
       -v ~/pg_temp:/usr/local/pgsql/temp \
       -p 0.0.0.0:5432:5432 \
       -d "hcr.data.here.com/naksha/postgres:arm64-${VERSION}"
# Show logs
docker logs naksha_pg
# Show generated password
cat ~/pg_data/postgres.pwd
# Test the db
psql "user=postgres sslmode=disable host=localhost dbname=unimap"
# When not okay, delete docker and repeat
docker stop naksha_pg
docker rm naksha_pg
docker image rm "${DR_NAKSHA_POSTGRES}:arm64-${VERSION}"
```

For this purpose, start the docker, if `pg-base` should be built, then start from the [Amazon Linux](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-container-image.html) image.

To start a new docker and do some manual builds (for debugging or playing around), do this:

- Start a totally new docker with a bash: `docker run -it amazonlinux bash`
- Or: `docker run -it hcr.data.here.com/naksha-devops/naksha-postgres:plv8-arm64-v16.2-r0 bash`
- If you exited a docker and want to re-enter, first see which one it is doing `docker ps -a`
- Ones found, restart via `docker start -ai <container-id>`, this gets you back to where you stopped

## Start a container
Before a Naksha PostgresQL contains is started it is recommended to create a directory where you want to store the database, if you want to persist it:

```bash
mkdir -p ~/pg_data
mkdir -p ~/pg_temp
```

To create the container do the following:

```bash
docker pull hcr.data.here.com/naksha/postgres:arm64-v16.2-r3
docker run --name naksha_pg \
       -v ~/pg_data:/usr/local/pgsql/data \
       -v ~/pg_temp:/usr/local/pgsql/temp \
       -p 0.0.0.0:5432:5432 \
       -d hcr.data.here.com/naksha/postgres:arm64-v16.2-r3
```

When the docker container is started for the first time, it will generate a random password for the `postgres` user and store it inside the docker container in `/home/postgres/postgres.pwd`. You should remember this, because the password is stored in the database. It as well prints it, you can review like:

```bash
docker logs naksha_pg
...
Initialized database with password: bLqzfifYRzfOoXtGvqUsmxQuxCsuhsqT
...
```

**Note**: You may have to change the platform (amd64/arm64) and the version of the container.

## Env-Vars
The docker container accepts the same environment variables that [libpq](https://www.postgresql.org/docs/current/libpq-envars.html) accepts:

- **PGDATABASE**: Database.
- **PGUSER**: User.
- **PGPASSWORD**: Password.

## Performance test
```bash
export NVMEPART=/dev/md0
fio --time_based --name=benchmark --size=100G --runtime=30 \
    --filename=$NVMEPART --ioengine=libaio --randrepeat=0 \
    --iodepth=128 --direct=1 --invalidate=1 --verify=0 --verify_fatal=0 \
    --numjobs=4 --rw=randwrite --blocksize=32k --group_reporting
```
