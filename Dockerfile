FROM openjdk:17-slim

LABEL maintainer="ashrafali.syed@here.com"

COPY here-naksha-app-service/target/here-naksha-app-service.jar .
ADD Dockerfile /

EXPOSE 8080 9090
CMD java -jar here-naksha-app-service.jar
