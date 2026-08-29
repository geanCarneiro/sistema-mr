# Stage 1: Build da aplicação
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Imagem final enxuta pra execução
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S application && adduser -S application -G application
COPY --from=build --chown=application:application /app/target/*.jar app.jar
USER application
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
