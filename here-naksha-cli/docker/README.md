# How to guide

> [!TIP]
> You can add variables for convenient.
> ```bash
> export NAMESPACE=yournamespace
> export IMAGE_NAME=yourimagename
> export TAG=yourtag
> ```

## Build image

- Move to `here-naksha-cli/docker` dir.
- To edit generation's parameters edit files in `naksha-cli-files` dir. Look at
  `here-naksha-cli/docs/GeneratingStorageConfig.md` to learn more.

> [!NOTE]
> There are defaults targetMapId="genmap" and targetCollectionId="gencol".
> You can change target's mapId and collectionId using build-args:
> ```bash
> docker build . \
> --build-arg targetMapId="tarmap" \
> --build-arg targetCollectionId="tarcol"
> ```
> Script uses cli-v0.1.0. You can also change it using build-arg:
> --build-arg CLI_GIT_TAG="cli-new-version"

- Run commands bellow to build images for specified platforms
    ```bash
    docker build . \
      --platform linux/amd64 \
      -t ghcr.io/${NAMESPACE}/${IMAGE_NAME}-amd64:${TAG}
    docker build . \
      --platform linux/arm64 \
      -t ghcr.io/${NAMESPACE}/${IMAGE_NAME}-arm64:${TAG}
    ```
- Create manifest
    ```bash
     docker manifest create ghcr.io/${NAMESPACE}/${IMAGE_NAME}:${TAG} \
        ghcr.io/${NAMESPACE}/${IMAGE_NAME}-amd64:${TAG} \
        ghcr.io/${NAMESPACE}/${IMAGE_NAME}-arm64:${TAG}
    ```

## Push image

> [!NOTE]  
> This guide covers pushing images to GitHub container repository, but you can push to other repos as well.

- [Authenticate to repo.](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-with-a-personal-access-token-classic)
- Push images
    ```bash
    docker manifest push ghcr.io/${NAMESPACE}/${IMAGE_NAME}:${TAG}
    ```

## Run container

> [!NOTE]
> Default credentials for the postgres `postgres`:`password`

```bash
   docker run -d \
      --name ${CONTAINER_NAME} \
      -p ${PORT}:5432 \
      ghcr.io/${NAMESPACE}/${IMAGE_NAME}:${TAG}
```

## Read more

- [Working with the GitHub Container registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)