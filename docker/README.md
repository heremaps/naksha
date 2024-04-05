### Running Naksha in container

Naksha can be run in container as well. So far, only locally build image can be used.\
To get Naksha container running, one must do the following:

1) CD into the root project directory (we need this because build context needs to access files that
   don't belong to `docker` directory).
     ```shell
    cd ..
    ```
2) Build the fat jar:
     ```shell
    ./gradlew shadowJar
    ```
3) Build the local image
    ```shell
   docker build -t local-naksha-app -f docker/Dockerfile .
    ```
4) Run the container:
   There are two optional environment variables that one can specify when running Naksha conrtainer
    - `NAKSHA_CONFIG_ID`: id of naksha configaration to use, `test-config` by default
    - `NAKSHA_DB_URL`: url of database for Naksha app to
      use, `jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password&schema=naksha&app=naksha_local&id=naksha_admin_db`
      by default

   When connecting Naksha app to database, one has to consider container networking - if your
   database is running locally, then you need to instruct the container to utilize host's
   network (`--network=host`).\
   Putting it all together the typical command you would use is:
   ```shell
   docker run \                                                                                                                                                                                                                              
      --network=host \
      --env NAKSHA_CONFIG_ID=<your Naksha config id> \
      --env NAKSHA_DB_URI=<your DB uri that Naksha should use> \
      localhost/local-naksha-app
    ```

### Additional remarks

#### Running in detached mode & finding your container id

Starting the container as in the sample above will hijack your terminal. To avoid this pass `-d`
flag (as in "detached")

   ```shell
   > docker run -d --network=host localhost/local-naksha-app
   3d38a7d51f7881a634da6d5f38c2beb642fe2bd201e63d309d84cecb1c3a81ee
   ```

The output of this command (`3d38...`) will be your `CONTAINER ID`. \
To find your `CONTAINER ID` you can also utilize `docker ps` with some basic filtering:

   ```shell
   > docker ps -q  --filter ancestor=localhost/local-naksha-app
   3d38a7d51f78
   ```

#### Tailing logs

If you want to tail logs of your running container (ie when you detached it before), you can
use [docker logs]() as in the sample:

   ```shell
   docker logs -f --tail 10 3d38a7d51f78   
   ```

The command above will start tailing logs from container with id `3d38...` and also print last 10
lines.

#### Stopping / killing container

To stop the running container simply run:

   ```shell
   docker stop 3d38a7d51f78 
   ```

Stopping is graceful, meaning - it sends `SIGTERM` to the process so the app will have some time to
perform the cleanup.\
If you need to stop the container immediately, use `docker kill 3d38a7d51f78` - the main difference
is that instead of `SIGTERM` the process will receive `SIGKILL`.

#### Removing the image

If you ever need to clean the image, use one of the below (the latter is simply the alias of the
former).

   ```
   > docker image rm localhost/local-naksha-app
   > docker rmi localhost/local-naksha-app
   ```

