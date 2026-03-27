# 1. ビルド（組み立て）用の環境
FROM maven:3.8.5-openjdk-25 AS build
COPY . .
RUN mvn clean package -DskipTests

# 2. 実行用の環境
FROM openjdk:25.0.1-jdk-slim
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]