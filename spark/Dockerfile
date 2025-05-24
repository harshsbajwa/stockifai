# Stage 1: Build the Spark application
FROM bellsoft/liberica-runtime-container:jdk-all-21-slim-stream-musl AS builder
WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

COPY spark/build.gradle.kts ./spark/build.gradle.kts
COPY spark/src ./spark/src

RUN chmod +x ./gradlew && ./gradlew :spark:jar --no-daemon

# Stage 2: Prepare Spark runtime environment
FROM bellsoft/liberica-runtime-container:jre-21-musl
WORKDIR /opt/spark-app

# Define Spark version and download URL
ARG SPARK_VERSION=3.5.5
ARG HADOOP_VERSION=3
ENV SPARK_HOME=/opt/spark
ENV PATH=$SPARK_HOME/bin:$SPARK_HOME/sbin:$PATH

# Install necessary tools download/extract Spark
RUN apk update && apk add --no-cache bash curl tar wget \
  && wget https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz -O /tmp/spark.tgz \
  && tar -xzf /tmp/spark.tgz -C /opt \
  && mv /opt/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} ${SPARK_HOME} \
  && rm /tmp/spark.tgz

COPY --from=builder /app/spark/build/libs/spark-processor.jar ./app.jar

# Create basic configuration files
RUN echo "spark.serializer=org.apache.spark.serializer.KryoSerializer" > $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.adaptive.enabled=true" >> $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.adaptive.coalescePartitions.enabled=true" >> $SPARK_HOME/conf/spark-defaults.conf

# Create basic log4j2 configuration
RUN echo "rootLogger.level=INFO" > $SPARK_HOME/conf/log4j2.properties && \
  echo "rootLogger.appenderRef.stdout.ref=STDOUT" >> $SPARK_HOME/conf/log4j2.properties && \
  echo "appender.console.type=Console" >> $SPARK_HOME/conf/log4j2.properties && \
  echo "appender.console.name=STDOUT" >> $SPARK_HOME/conf/log4j2.properties && \
  echo "appender.console.layout.type=PatternLayout" >> $SPARK_HOME/conf/log4j2.properties && \
  echo "appender.console.layout.pattern=%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n" >> $SPARK_HOME/conf/log4j2.properties

WORKDIR /opt/spark-app