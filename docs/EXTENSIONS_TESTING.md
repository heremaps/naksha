# Testing Naksha Service with Extensions

This guide explains how to test Naksha service with custom extensions, including configuration, running the service, and verifying extension behavior.
For demonstration purposes, we will use a sample `test` extension that adds the tag `test_tag_v1` to the feature provided in the writeFeature request.

---

## 1. Define custom startup config for Naksha

Extension manager configuration is defined in `extensionConfigParams` as part of Naksha startup config.  
Refer to the [EXT_MANAGER - Configuration Section](../docs/EXT_MANAGER.md#configuration) for general config guidance.

In this example, we are using a local folder.

### Example
```json
{
  "id": "extension-config",
  "type": "Config",
  // ... other config parameters
  "extensionConfigParams": {
    "whitelistClasses": [
      "java.*",
      "javax.*",
      "com.here.*",
      "jdk.internal.reflect.*",
      "com.sun.*",
      "org.w3c.dom.*",
      "sun.misc.*"
    ],
    "intervalms": 10000,
    "extensionsRootPath": "file:///app/naksha/extensions/"
  }
}
```
## 2. Start Naksha service with custom config
Start Naksha Service with your custom configuration. Follow the steps below:
### Steps:
1. **Prepare the config directory** as per the configuration instructions: [Naksha Service Configuration](../README.md#configuration).
2. **Start Naksha Service using the custom config**. For example, you can run:  
```bash
java -jar build/libs/naksha-2.2.12-all.jar test-config
```

## 3. Releasing a Custom Extension for Specific Environment

Refer to the [`EXT_MANAGER` documentation](../docs/EXT_MANAGER.md#folder-structure) for full folder structure and content guidelines.

We are using below folder structure and `local` environment for our testing. All extensions should reside under `extensionsRootPath` with the following layout:
```text
  naksha
  |___extensions
      |___naksha-test-extension
          |___latest-local.txt  
          |___naksha-test-extension-1.0.0.local.json
          |___naksha-test-extension-1.0.0-shaded.jar
```

Contents of `latest-local.txt`:
```text
1.0.0
```

Contents of `naksha-test-extension-1.0.0.local.json`:
```json
{
      "id": "naksha-test-extension",
      "type": "Extension",
      "url": "file:///app/naksha/extensions/naksha-test-extension/naksha-test-extension-1.0.0-shaded.jar",
      "version": "1.0.0",
      "initClassName": "",
      "properties": {
        "whitelistClasses": ["java.*", "javax.*", "com.here.*", "jdk.internal.reflect.*", "com.sun.*", "org.w3c.dom.*", "sun.misc.*", "org.locationtech.jts.*", "org.xml.sax.*", "org.slf4j.*"]
      }
}
```
## 4. Define Custom EventHandler

When defining an EventHandler using an extension, use the naming convention: `<env>:<extension-id>`. The <env> value ensures that the correct version of extension is loaded from the <extensionsRootPath>.
The `className` field should be the fully qualified class name of your handler.

This JSON configuration should be submitted to Naksha using the REST API endpoint: **POST /hub/handlers**

### Example:

```json
{
  "id": "test_handler",
  "type": "EventHandler",
  "title": "Sample Handler for Testing",
  "description": "Handler that returns success result for read requests",
  "className": "com.here.naksha.ext.test.handlers.TestHandler",
  "active": true,
  "extensionId": "local:naksha-test-extension",
  "properties": {
    "skipTagging": false
  }
}
```

## 5. Verify Extension Loading into Naksha 
Verify that after creating the custom EventHandler, the extension is successfully loaded into the Naksha service. 
You can confirm this by checking the log statements, which should look similar to the following:
```text
2025-09-23 10:38:15.349 -0500 [INFO ]  [NakshaWorker#1] - lib.extmanager.ExtensionCache (downloadJar:201) {streamId=8Il23zBXwvzL} - Downloading jar naksha-test-extension with version 1.0.0  
2025-09-23 10:38:15.350 -0500 [INFO ]  [main] - lib.extmanager.ExtensionCache (publishIntoCache:111) {streamId=naksha-app} - Whitelist classes in use for extension local:naksha-test-extension are [java.*, javax.*, com.here.*, jdk.internal.reflect.*, com.sun.*, org.w3c.dom.*, sun.misc.*, org.locationtech.jts.*, org.xml.sax.*, org.slf4j.*] 
2025-09-23 10:38:15.360 -0500 [INFO ]  [main] - lib.extmanager.ExtensionCache (publishIntoCache:153) {streamId=naksha-app} - Extension id=local:naksha-test-extension, version=1.0.0 is successfully loaded into the cache, using Jar at naksha-test-extension-1.0.0-shaded.jar. 
2025-09-23 10:38:15.361 -0500 [INFO ]  [main] - lib.extmanager.ExtensionCache (buildExtensionCache:96) {streamId=naksha-app} - Extension cache size 1 
```

## 6. Defining Spaces

Create a **Space** that uses the custom EventHandler.

This JSON configuration should be submitted to Naksha using the REST API endpoint: **POST /hub/spaces**

### Example JSON:

```json
{
  "type": "Space",
  "eventHandlerIds": ["test_handler"],
  "id": "test_space",
  "description": "Space with a handler that returns success for any request",
  "title": "Test Space for Success Handler"
}
```

## 7. API Testing

Once your space is created with the custom EventHandler, you can test the extension by triggering API requests against this space. The extension used in this example returns a success response for any request.

### API Request Example
```curl
curl -X 'POST' \
  'http://localhost:8080/hub/spaces/test_space/features' \
  -H 'accept: application/geo+json' \
  -H 'Content-Type: application/geo+json' \
  -d '{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Point",
        "coordinates": [
          8.68872,
          50.0561,
          292.94377758
        ]
      },
      "properties": {
        "name": "Anfield",
        "@ns:com:here:xyz": {
          "tags": [
            "football",
            "stadium"
          ]
        },
        "amenity": "Football Stadium",
        "capacity": 54074,
        "description": "Home of Liverpool Football Club"
      }
    }
  ]
}'
```
### Response
See in the response that the new tag `test_tag_v1` has been added by our extension:
```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id" : "BfiimUxHjj",
      "geometry": {
        "type": "Point",
        "coordinates": [8.68872, 50.0561, 292.94377758]
      },
      "properties": {
        "name": "Anfield",
        "@ns:com:here:xyz": {
          "tags": ["football", "stadium", "test_tag_v1"],
          "updatedAt" : 1517504700726
        },
        "amenity": "Football Stadium",
        "capacity": 54074,
        "description": "Home of Liverpool Football Club"
      }
    }
  ],
  "streamId": "3Yr6hZRsTSS9"
}
```
Check the Naksha service logs to ensure the extension was invoked correctly. 
Example relevant log entries:
```text
2025-09-24 15:30:48.454 -0500 [INFO ]  [NakshaWorker#2] - lib.hub.storages.NHSpaceStorageReader (setupEventPipelineForSpaceId:289) {streamId=3Yr6hZRsTSS9} - Handler IDs identified [test_handler] 
2025-09-24 15:30:48.455 -0500 [INFO ]  [NakshaWorker#2] - lib.hub.storages.NHSpaceStorageReader (setupEventPipelineForSpaceId:338) {streamId=3Yr6hZRsTSS9} - Handler types identified [TestHandler] 
2025-09-24 15:30:48.455 -0500 [INFO ]  [NakshaWorker#2] - ext.test.handlers.TestHandler (processEvent:45) {streamId=3Yr6hZRsTSS9} - Handler received request WriteXyzFeatures 
2025-09-24 15:30:48.465 -0500 [INFO ]  [NakshaWorker#2] - app.service.util.logging.AccessLogUtil (writeAccessLog:219) {streamId=3Yr6hZRsTSS9} - {"time":"2025-09-24T20:30:48,464","clientInfo":{"appId":"naksha","ip":"0:0:0:0:0:0:0:1","realm":null,"remoteAddress":"0:0:0:0:0:0:0:1:60076","userAgent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36","userId":"master-test-user"},"ms":37,"ns":37950490,"reqInfo":{"accept":"application/geo+json","contentType":"application/geo+json","method":"POST","origin":"http://localhost:8080","referer":"http://localhost:8080/hub/swagger/index.html","size":540,"uri":"/hub/spaces/test_space/features"},"respInfo":{"contentType":"application/geo+json","size":68,"statusCode":200,"statusMsg":"OK"},"src":null,"streamId":"3Yr6hZRsTSS9","streamInfo":{"spaceId":"test_space","timeInStorageMs":0},"t":"STREAM","timeWithoutStorageMs":37,"unixtime":1758745848464} 
2025-09-24 15:30:48.465 -0500 [INFO ]  [NakshaWorker#2] - app.service.util.logging.AccessLogUtil (writeAccessLog:225) {streamId=3Yr6hZRsTSS9} - [REST API stats => spaceId,storageId,method,uri,status,timeTakenMs,resSize,timeWithoutStorageMs] - RESTAPIStats test_space - POST /hub/spaces/test_space/features 200 37 68 37 
```

<details> <summary>Complete logs </summary>

```text
2025-09-24 15:30:48.426 -0500 [INFO ]  [vert.x-eventloop-thread-16] - app.service.util.logging.AccessLogUtil (addRequestInfo:159) {streamId=3Yr6hZRsTSS9} - Request POST - /hub/spaces/test_space/features 
2025-09-24 15:30:48.440 -0500 [INFO ]  [NakshaWorker#2] - app.service.http.tasks.WriteFeatureApiTask (execute:88) {streamId=3Yr6hZRsTSS9} - Received Http request CREATE_FEATURES 
2025-09-24 15:30:48.447 -0500 [INFO ]  [NakshaWorker#2] - lib.hub.storages.NHSpaceStorageReader (executeReadFeatures:216) {streamId=3Yr6hZRsTSS9} - ReadFeatures Request against spaceId=test_space 
2025-09-24 15:30:48.447 -0500 [INFO ]  [NakshaWorker#2] - lib.psql.PostgresStorage (initSession:327) {streamId=3Yr6hZRsTSS9} - Init session using stmtTimeout=60000ms, lockTimeout=1000ms 
2025-09-24 15:30:48.449 -0500 [INFO ]  [NakshaWorker#2] - lib.hub.storages.NHSpaceStorageReader (setupEventPipelineForSpaceId:289) {streamId=3Yr6hZRsTSS9} - Handler IDs identified [test_handler] 
2025-09-24 15:30:48.451 -0500 [INFO ]  [NakshaWorker#2] - lib.hub.storages.NHSpaceStorageReader (setupEventPipelineForSpaceId:338) {streamId=3Yr6hZRsTSS9} - Handler types identified [TestHandler] 
2025-09-24 15:30:48.451 -0500 [INFO ]  [NakshaWorker#2] - ext.test.handlers.TestHandler (processEvent:45) {streamId=3Yr6hZRsTSS9} - Handler received request ReadFeaturesProxyWrapper 
2025-09-24 15:30:48.451 -0500 [INFO ]  [NakshaWorker#2] - lib.hub.storages.NHSpaceStorageWriter (executeWriteFeatures:101) {streamId=3Yr6hZRsTSS9} - WriteFeatures Request against spaceId=test_space 
2025-09-24 15:30:48.452 -0500 [INFO ]  [NakshaWorker#2] - lib.psql.PostgresStorage (initSession:327) {streamId=3Yr6hZRsTSS9} - Init session using stmtTimeout=60000ms, lockTimeout=1000ms 
2025-09-24 15:30:48.454 -0500 [INFO ]  [NakshaWorker#2] - lib.hub.storages.NHSpaceStorageReader (setupEventPipelineForSpaceId:289) {streamId=3Yr6hZRsTSS9} - Handler IDs identified [test_handler] 
2025-09-24 15:30:48.455 -0500 [INFO ]  [NakshaWorker#2] - lib.hub.storages.NHSpaceStorageReader (setupEventPipelineForSpaceId:338) {streamId=3Yr6hZRsTSS9} - Handler types identified [TestHandler] 
2025-09-24 15:30:48.455 -0500 [INFO ]  [NakshaWorker#2] - ext.test.handlers.TestHandler (processEvent:45) {streamId=3Yr6hZRsTSS9} - Handler received request WriteXyzFeatures 
2025-09-24 15:30:48.465 -0500 [INFO ]  [NakshaWorker#2] - app.service.util.logging.AccessLogUtil (writeAccessLog:219) {streamId=3Yr6hZRsTSS9} - {"time":"2025-09-24T20:30:48,464","clientInfo":{"appId":"naksha","ip":"0:0:0:0:0:0:0:1","realm":null,"remoteAddress":"0:0:0:0:0:0:0:1:60076","userAgent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36","userId":"master-test-user"},"ms":37,"ns":37950490,"reqInfo":{"accept":"application/geo+json","contentType":"application/geo+json","method":"POST","origin":"http://localhost:8080","referer":"http://localhost:8080/hub/swagger/index.html","size":540,"uri":"/hub/spaces/test_space/features"},"respInfo":{"contentType":"application/geo+json","size":68,"statusCode":200,"statusMsg":"OK"},"src":null,"streamId":"3Yr6hZRsTSS9","streamInfo":{"spaceId":"test_space","timeInStorageMs":0},"t":"STREAM","timeWithoutStorageMs":37,"unixtime":1758745848464} 
2025-09-24 15:30:48.465 -0500 [INFO ]  [NakshaWorker#2] - app.service.util.logging.AccessLogUtil (writeAccessLog:225) {streamId=3Yr6hZRsTSS9} - [REST API stats => spaceId,storageId,method,uri,status,timeTakenMs,resSize,timeWithoutStorageMs] - RESTAPIStats test_space - POST /hub/spaces/test_space/features 200 37 68 37 
2025-09-24 15:30:48.465 -0500 [INFO ]  [NakshaWorker#2] - app.service.http.NakshaHttpVerticle (sendRawResponse:694) {streamId=3Yr6hZRsTSS9} - Returned Http status 200 
2025-09-24 15:30:50.815 -0500 [INFO ]  [Thread-1] - lib.extmanager.ExtensionCache (run:40) {} - Extension cache refresh job started
```

</details>

## 8. Releasing New Versions

When releasing a new version of an extension, Naksha will automatically detect changes and reload the extension if any of the following fields differ from the currently loaded version **by unloading the previously loaded extension version (if any)**:
- `url` of the extension JAR
- `version`
- `initClassName`

For **cloud deployments**, these changes are automatically handled by the GitLab pipeline. For **local testing**, you need to make changes manually.

### Steps to Release a New Version:
1. Update `latest-<env>.txt` with the new version number (e.g., `1.1.0`).
2. Place the new `.jar` and the corresponding updated JSON configuration file in the extension folder.
3. **No need to restart Naksha Service**; it will check periodically (based on `intervalms`) and reload the extension if changes are detected.

---

## 9. IntelliJ Debugging

Debugging support is essential when working with dynamically loaded extensions. Since Naksha loads extensions at runtime, you will need to attach a remote debugger.

Refer to the [IntelliJ Remote Debugging Guide](https://www.baeldung.com/intellij-remote-debugging) for step-by-step instructions on setting up and attaching the debugger.
