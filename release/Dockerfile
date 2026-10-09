FROM eclipse-temurin:25-jre
WORKDIR /app
COPY personal-website-api.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
