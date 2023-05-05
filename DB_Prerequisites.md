# Database Prerequisites for Configuring Naksa (नक्शा)

## Please make sure you have below setup done before starting Naksha service.

1. Make sure you have valid database available with you. It might be local database or with docker.
2. Create schema "xyz_config" in your database.
3. Check if you have Postgis extension in your db, if not run below query.
    ```sql
    CREATE EXTENSION postgis
    ```
    If you are using docker you can directly run below command, it will install Postgis extension as well.
    ```docker
    docker run --name NakshaLocalhost -p 5432:5432 -e POSTGRES_PASSWORD=password -d postgres/postgis
    ```
4. Run sql script [Naksha_ext.sql](https://github.com/xeus2001/xyz-hub/blob/Naksha_master/xyz-psql-connector/src/main/resources/naksha_ext.sql) in you database.
5. Go to [config.json](https://github.com/xeus2001/xyz-hub/blob/Naksha_master/xyz-hub-service/src/main/resources/config.json) and edit below details.
   <br> **Note**: If you are using existing database then update details accordingly. 
   ```
      "STORAGE_DB_URL": "jdbc:postgresql://localhost/postgres",
      "STORAGE_DB_USER": "postgres",
      "STORAGE_DB_PASSWORD": {$password},
      "PSQL_HTTP_CONNECTOR_HOST": "localhost",
      "PSQL_HTTP_CONNECTOR_PORT": 5432,
    ```
6. Now start the [service](https://github.com/xeus2001/xyz-hub/blob/Naksha_master/xyz-hub-service/src/main/java/com/here/xyz/hub/Service.java). It should run without any errors.
   You can also use jar command for the same.
    ```
    java -jar xyz-hub-service/target/xyz-hub-service.jar
    ```
7. To verify if the basic api calls are working fine. Try below commands.
   To get list of spaces:
   ```
   curl 'http://localhost:8080/hub/spaces?includeRights=false&includeConnectors=false&owner=%2A' -H 'accept: application/json' | jq 
   ```
   To create a new space:
   ```
   curl -H "content-type:application/json" -d '{"id": "test-space", "title": "my first space", "description": "my first geodata repo"}' 
   "http://localhost:8080/hub/spaces"
   ```
   
