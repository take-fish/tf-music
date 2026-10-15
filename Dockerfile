FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/tf-music.jar /app/app.jar
EXPOSE 8105
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
