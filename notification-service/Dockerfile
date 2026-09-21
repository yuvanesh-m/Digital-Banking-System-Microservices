#notification-service/ Dockerfile

# Step 1 : Use Java 17 as base image
FROM eclipse-temurin:17-jre

# Step 2 : Set Working directory inside container
WORKDIR /app

# Step 3 : Copy Build jar file
COPY target/notification-service-0.0.1-SNAPSHOT.jar app.jar

# Step 4 : Expose port
EXPOSE 8085

# Step 5 : Run the jar
ENTRYPOINT ["java","-jar","app.jar"]

