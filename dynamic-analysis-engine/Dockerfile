FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the built JAR file
COPY target/*.jar app.jar

# Expose health check port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
