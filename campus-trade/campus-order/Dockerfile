FROM amazoncorretto:17-alpine@sha256:223ebda8690566648f5cc77a69d661ecf7c35a9f977fd7cbf0d19545491ad1c6
RUN addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app -h /app app
WORKDIR /app
COPY --chown=app:app target/campus-order-1.0.0-SNAPSHOT.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8083
USER app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
