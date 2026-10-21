FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY Detector.java StreamWatch.java index.html ./
RUN javac Detector.java StreamWatch.java
ENV HOST=0.0.0.0 PORT=8080
EXPOSE 8080
USER 10001
CMD ["java", "StreamWatch"]
