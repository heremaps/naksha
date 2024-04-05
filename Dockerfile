FROM arm64v8/eclipse-temurin:17

ARG NAKSHA_VER
RUN test -n ${NAKSHA_VER:?}

RUN mkdir /opt/app
ENV NAKSHA_CONTAINER_JAR /opt/naksha-${NAKSHA_VER}-all.jar
COPY build/libs/naksha-${NAKSHA_VER}-all.jar $NAKSHA_CONTAINER_JAR

ENV CONFIG_ID test-config
ENV DB_URI 'jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password&schema=naksha&app=naksha_local&id=naksha_admin_db'

CMD ["sh", "-c", "java -jar $NAKSHA_CONTAINER_JAR $CONFIG_ID $DB_URI"]
