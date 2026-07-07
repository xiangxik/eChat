FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S echat && adduser -S echat -G echat
WORKDIR /app
COPY --from=build /workspace/target/chatbot-service-*.jar /app/app.jar

USER echat
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
