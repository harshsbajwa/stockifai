# Stage 1: Build the Spark application
FROM bellsoft/liberica-sdk:17-alpaquita AS builder
WORKDIR /app

COPY ../gradlew .
COPY ../gradle ./gradle
COPY ../build.gradle.kts .
COPY ../settings.gradle.kts .

COPY build.gradle.kts ./spark/build.gradle.kts
COPY src ./processor/src

RUN chmod +x ./gradlew && ./gradlew :spark:jar --no-daemon

# Stage 2: Prepare Spark runtime environment
FROM bellsoft/liberica-sdk:17-alpaquita
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

COPY --from=builder /app/spark-processor/build/libs/*.jar ./app.jar

COPY spark-defaults.conf $SPARK_HOME/conf/spark-defaults.conf
COPY log4j2.properties $SPARK_HOME/conf/log4j2.properties

WORKDIR /opt/spark-app