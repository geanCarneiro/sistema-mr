# Stage 1: Build da aplicação
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Imagem final enxuta pra execução
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache python3 py3-pip && \
    ln -sf /usr/bin/python3 /usr/bin/python
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]