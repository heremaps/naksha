FROM arm64v8/eclipse-temurin:17
RUN mkdir /opt/app
COPY build/libs/naksha-2.0.15-all.jar /opt/naksha-2.0.15-all.jar

ENV CONFIG_ID test-config
# when using local db, remember to specify network: docker run --network=host localhost/naksha-test
ENV DB_URI 'jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password&schema=naksha&app=naksha_local&id=naksha_admin_db'

CMD ["sh", "-c", "java -jar /opt/naksha-2.0.15-all.jar $CONFIG_ID $DB_URI"]
