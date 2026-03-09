FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 18080

ENV SERVER_PORT=18080
ENV SERVER_ADDRESS=0.0.0.0
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${SERVER_PORT:-18080} -Dserver.address=${SERVER_ADDRESS:-0.0.0.0} -jar /app/app.jar"]
