FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/campus-user-1.0.0-SNAPSHOT.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
