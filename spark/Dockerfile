# Stage 1: Build the Spark application
FROM bellsoft/liberica-runtime-container:jdk-all-17-slim-stream-musl AS builder
WORKDIR /app

# Copy entire project structure for multi-module build
COPY gradlew .
COPY gradlew.bat .
COPY gradle/ ./gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy all module build files first (for better caching)
COPY api/build.gradle.kts ./api/
COPY spark/build.gradle.kts ./spark/
COPY stream/build.gradle.kts ./stream/

# Copy Avro schemas and source code
COPY spark/src/ ./spark/src/

RUN chmod +x ./gradlew && ./gradlew :spark:jar --no-daemon

# Stage 2: Prepare Spark runtime environment
FROM bellsoft/liberica-runtime-container:jre-17-crac-glibc
WORKDIR /opt/spark-app

# Define Spark version and download URL
ARG SPARK_VERSION=3.5.5
ARG HADOOP_VERSION=3
ENV SPARK_HOME=/opt/spark
ENV HADOOP_HOME=${SPARK_HOME}
ENV PATH=$SPARK_HOME/bin:$SPARK_HOME/sbin:$PATH

# Install necessary tools and download/extract Spark
RUN apk update && apk add --no-cache bash curl tar wget \
  && wget https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz -O /tmp/spark.tgz \
  && tar -xzf /tmp/spark.tgz -C /opt \
  && mv /opt/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} ${SPARK_HOME} \
  && rm /tmp/spark.tgz

# Download required Kafka and Avro jars for Spark
RUN wget https://repo1.maven.org/maven2/org/apache/spark/spark-sql-kafka-0-10_2.12/${SPARK_VERSION}/spark-sql-kafka-0-10_2.12-${SPARK_VERSION}.jar -O ${SPARK_HOME}/jars/spark-sql-kafka-0-10_2.12-${SPARK_VERSION}.jar \
  && wget https://repo1.maven.org/maven2/org/apache/spark/spark-avro_2.12/${SPARK_VERSION}/spark-avro_2.12-${SPARK_VERSION}.jar -O ${SPARK_HOME}/jars/spark-avro_2.12-${SPARK_VERSION}.jar \
  && wget https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.5.0/kafka-clients-3.5.0.jar -O ${SPARK_HOME}/jars/kafka-clients-3.5.0.jar \
  && wget https://repo1.maven.org/maven2/org/apache/commons/commons-pool2/2.11.1/commons-pool2-2.11.1.jar -O ${SPARK_HOME}/jars/commons-pool2-2.11.1.jar \
  && wget https://packages.confluent.io/maven/io/confluent/kafka-avro-serializer/7.9.1/kafka-avro-serializer-7.9.1.jar -O ${SPARK_HOME}/jars/kafka-avro-serializer-7.9.1.jar \
  && wget https://packages.confluent.io/maven/io/confluent/kafka-schema-registry-client/7.9.1/kafka-schema-registry-client-7.9.1.jar -O ${SPARK_HOME}/jars/kafka-schema-registry-client-7.9.1.jar \
  && wget https://packages.confluent.io/maven/io/confluent/common-config/7.9.1/common-config-7.9.1.jar -O ${SPARK_HOME}/jars/common-config-7.9.1.jar \
  && wget https://packages.confluent.io/maven/io/confluent/common-utils/7.9.1/common-utils-7.9.1.jar -O ${SPARK_HOME}/jars/common-utils-7.9.1.jar \
  && wget https://repo1.maven.org/maven2/org/apache/avro/avro/1.11.3/avro-1.11.3.jar -O ${SPARK_HOME}/jars/avro-1.11.3.jar \
  && wget https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.2/jackson-core-2.15.2.jar -O ${SPARK_HOME}/jars/jackson-core-2.15.2.jar \
  && wget https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.2/jackson-databind-2.15.2.jar -O ${SPARK_HOME}/jars/jackson-databind-2.15.2.jar

COPY --from=builder /app/spark/build/libs/spark-processor.jar ./app.jar

# Create Spark configuration files
RUN echo "spark.serializer=org.apache.spark.serializer.KryoSerializer" > $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.adaptive.enabled=true" >> $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.adaptive.coalescePartitions.enabled=true" >> $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.streaming.forceDeleteTempCheckpointLocation=true" >> $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.avro.compression.codec=snappy" >> $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.adaptive.skewJoin.enabled=true" >> $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.avro.datetimeRebaseModeInRead=CORRECTED" >> $SPARK_HOME/conf/spark-defaults.conf && \
  echo "spark.sql.avro.datetimeRebaseModeInWrite=CORRECTED" >> $SPARK_HOME/conf/spark-defaults.conf

# Copy log4j2 configuration
COPY spark/src/main/resources/log4j2.xml $SPARK_HOME/conf/log4j2.xml

# Environment variables for the application
ENV KAFKA_BOOTSTRAP_SERVERS=kafka:9093
ENV SCHEMA_REGISTRY_URL=http://schema-registry:8081
ENV INFLUXDB_URL=http://influxdb:8086
ENV INFLUXDB_TOKEN=mySuperSecretToken123!
ENV INFLUXDB_ORG=stockifai
ENV INFLUXDB_BUCKET=stockdata
ENV CASSANDRA_HOST=cassandra
ENV CASSANDRA_PORT=9042
ENV CASSANDRA_KEYSPACE=stock_keyspace

# Set JVM arguments for Java 17+ compatibility
ENV JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens=java.base/sun.security.action=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.net=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.fs=ALL-UNNAMED \
  --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
  --add-opens=java.base/java.nio.channels=ALL-UNNAMED \
  --add-opens=java.base/java.security=ALL-UNNAMED \
  --add-opens=java.base/java.time=ALL-UNNAMED \
  --add-opens=java.base/java.text=ALL-UNNAMED \
  --add-opens=java.management/sun.management=ALL-UNNAMED \
  -Dio.netty.tryReflectionSetAccessible=true \
  -Dlog4j.configurationFile=$SPARK_HOME/conf/log4j2.xml"

WORKDIR /opt/spark-app

# Entry point to run the Spark application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /opt/spark-app/app.jar:/opt/spark/jars/* com.harshsbajwa.stockifai.processing.RiskCalculationEngineKt"]