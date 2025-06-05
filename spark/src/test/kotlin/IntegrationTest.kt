package com.harshsbajwa.stockifai.processing

import com.harshsbajwa.stockifai.avro.finnhub.StockCandle
import com.harshsbajwa.stockifai.avro.fred.EconomicObservation
import org.apache.avro.io.BinaryEncoder
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.avro.util.Utf8
import org.apache.spark.sql.SparkSession
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
class RiskCalculationEngineIntegrationTest {
    companion object {
        private val logger = LoggerFactory.getLogger(RiskCalculationEngineIntegrationTest::class.java)

        private val network = Network.newNetwork()

        @Container
        val kafkaContainer =
            ConfluentKafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.9.1"),
            ).withNetwork(network)
                .withNetworkAliases("kafka")

        private lateinit var sparkSession: SparkSession

        @JvmStatic
        @BeforeAll
        fun setUp() {
            logger.info("Setting up test environment...")

            assertTrue(kafkaContainer.isRunning, "Kafka container should be running")

            // Setup Spark
            sparkSession =
                SparkSession
                    .builder()
                    .appName("RiskCalculationEngineTest")
                    .master("local[2]")
                    .config("spark.sql.adaptive.enabled", "true")
                    .config("spark.sql.streaming.forceDeleteTempCheckpointLocation", "true")
                    .getOrCreate()

            logger.info("Test environment ready")
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            sparkSession.stop()
            logger.info("Test cleanup completed")
        }
    }

    @Test
    fun `should process Avro stock data correctly`() {
        logger.info("Testing Avro stock data processing...")

        val stockCandle =
            StockCandle
                .newBuilder()
                .setSymbol(Utf8("AAPL"))
                .setOpen(150.0)
                .setHigh(155.0)
                .setLow(149.0)
                .setClose(154.0)
                .setVolume(1000000L)
                .setTimestamp(Instant.now())
                .build()

        val avroBytes = serializeAvroRecord(stockCandle)

        assertNotNull(avroBytes)
        assertTrue(avroBytes.isNotEmpty())

        logger.info("Successfully created and serialized Avro StockCandle record")
    }

    @Test
    fun `should process Avro economic data correctly`() {
        logger.info("Testing Avro economic data processing...")

        val economicObs =
            EconomicObservation
                .newBuilder()
                .setSeriesId(Utf8("VIXCLS"))
                .setObservationDate(Utf8("2024-01-15"))
                .setValue(Utf8("18.75"))
                .setRealTimeStart(Utf8("2024-01-15"))
                .setRealTimeEnd(Utf8("2024-01-15"))
                .build()

        val avroBytes = serializeAvroRecord(economicObs)

        assertNotNull(avroBytes)
        assertTrue(avroBytes.isNotEmpty())

        logger.info("Successfully created and serialized Avro EconomicObservation record")
    }

    private fun <T> serializeAvroRecord(record: T): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val encoder: BinaryEncoder = EncoderFactory.get().binaryEncoder(outputStream, null)

        @Suppress("UNCHECKED_CAST")
        val writer = SpecificDatumWriter(record!!::class.java as Class<T>)
        writer.write(record, encoder)
        encoder.flush()

        return outputStream.toByteArray()
    }
}
