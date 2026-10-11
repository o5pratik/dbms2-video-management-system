FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /project
COPY backend/pom.xml backend/pom.xml
RUN mvn -q -f backend/pom.xml dependency:go-offline
COPY backend backend
COPY frontend frontend
RUN mvn -q -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /project/backend/target/video-management-system-1.0.0.jar app.jar
ENV APP_HOST=0.0.0.0
ENV APP_PORT=10000
EXPOSE 10000
USER 10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
