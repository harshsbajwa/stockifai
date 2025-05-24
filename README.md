# StockiFAI - Real-time Stock Market Volatility Monitor

A comprehensive real-time stock market volatility and risk monitoring system built with modern technologies.

## Architecture

- **Data Collection**: Spring Boot service collecting data from Yahoo Finance, Alpha Vantage, and FRED APIs
- **Stream Processing**: Apache Spark for real-time data processing and analytics
- **Message Queue**: Apache Kafka for reliable message streaming
- **Time Series Database**: InfluxDB for storing time-series financial data
- **Operational Database**: Cassandra for storing processed results and metadata
- **Monitoring**: Prometheus for metrics collection
- **Visualization**: Grafana for dashboards and monitoring
- **API**: Spring Boot REST API for data access

## Prerequisites

1. **Java 21** - Download from [Adoptium](https://adoptium.net/) or use included Liberica JDK
2. **Docker & Docker Compose** - For running the infrastructure
3. **Git** - For version control
4. **API Keys** (Optional but recommended):
   - [Alpha Vantage API Key](https://www.alphavantage.co/support/#api-key) (Free tier available)
   - [FRED API Key](https://fred.stlouisfed.org/docs/api/api_key.html) (Free)

## Quick Start

### 1. Clone and Setup

```bash
git clone <https://github.com/harshsbajwa/stockifai>
cd stockifai
```

### 2. Configure Environment

Copy the example environment file:

```bash
# Windows
copy .env.example .env

# Unix/Linux/macOS
cp .env.example .env
```

Edit `.env` file and add your API keys:

```env
# API Configuration
ALPHAVANTAGE_API_KEY=your_alphavantage_key_here
FRED_API_KEY=your_fred_key_here

# Stock symbols to monitor (comma-separated)
MONITORED_STOCKS=AAPL,GOOGL,MSFT,TSLA,AMZN,NVDA,META,NFLX,CRM,AMD

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9094
SPRING_PROFILES_ACTIVE=local

# Database Configuration
INFLUXDB_TOKEN=mySuperSecretToken123!
INFLUXDB_ORG=stockifai
INFLUXDB_BUCKET=stockdata
```

### 3. Run Setup Script

**Windows:**
```cmd
setup.bat
```

**Unix/Linux/macOS:**
```bash
chmod +x setup.sh
./setup.sh
```

### 4. Manual Setup (Alternative)

If you prefer to run commands manually:

```bash
# Build the project
./gradlew clean build

# Start infrastructure services
docker-compose up -d zookeeper kafka influxdb cassandra

# Wait for services to start (30-60 seconds)
sleep 30

# Create Kafka topics
docker-compose exec kafka kafka-topics.sh --create --topic stock_prices --bootstrap-server localhost:9093 --partitions 3 --replication-factor 1
docker-compose exec kafka kafka-topics.sh --create --topic economic_indicators --bootstrap-server localhost:9093 --partitions 3 --replication-factor 1
docker-compose exec kafka kafka-topics.sh --create --topic intraday_data --bootstrap-server localhost:9093 --partitions 3 --replication-factor 1

# Start all services
docker-compose up -d
```

## Service Endpoints

| Service | URL | Description |
|---------|-----|-------------|
| Stream Service | http://localhost:8081 | Data collection service |
| API Service | http://localhost:8000 | REST API for data access |
| InfluxDB UI | http://localhost:8086 | Time-series database UI |
| Grafana | http://localhost:3000 | Dashboards (admin/admin) |
| Prometheus | http://localhost:9090 | Metrics collection |
| Kafka | localhost:9094 | Message broker (external) |
| Cassandra | localhost:9042 | NoSQL database |

## Monitoring and Logs

### View Service Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f stream
docker-compose logs -f api
docker-compose logs -f spark
```

### Health Checks
```bash
# Stream service health
curl http://localhost:8081/actuator/health

# API service health
curl http://localhost:8000/actuator/health
```

### Kafka Topics
```bash
# List topics
docker-compose exec kafka kafka-topics.sh --list --bootstrap-server localhost:9093

# View messages
docker-compose exec kafka kafka-console-consumer.sh --topic stock_prices --bootstrap-server localhost:9093 --from-beginning
```

## Data Flow

1. **Data Collection** (Stream Service):
   - Collects stock prices from Yahoo Finance API every 30 seconds
   - Collects market volatility (VIX) every 5 minutes
   - Collects economic indicators from FRED API every 15 minutes
   - Collects intraday data from Alpha Vantage every 10 minutes

2. **Message Processing** (Kafka):
   - `stock_prices`: Real-time stock price updates
   - `economic_indicators`: Economic data (VIX, Fed rates, etc.)
   - `intraday_data`: Detailed intraday trading data

3. **Stream Processing** (Spark):
   - Processes incoming Kafka messages
   - Calculates volatility metrics
   - Performs risk analysis
   - Stores results in InfluxDB and Cassandra

4. **Data Storage**:
   - **InfluxDB**: Time-series data for real-time metrics
   - **Cassandra**: Processed results and historical data

5. **Visualization** (Grafana):
   - Real-time dashboards
   - Historical trend analysis
   - Risk monitoring alerts

## Configuration

### Stock Symbols
Edit the `MONITORED_STOCKS` environment variable in `.env`:
```env
MONITORED_STOCKS=AAPL,GOOGL,MSFT,TSLA,AMZN,NVDA,META,NFLX,CRM,AMD
```

### Data Collection Intervals
Modify the `@Scheduled` annotations in `stream/src/main/kotlin/Application.kt`:
- Stock data: `fixedRate = 30000` (30 seconds)
- Volatility data: `fixedRate = 300000` (5 minutes)
- Economic indicators: `fixedRate = 900000` (15 minutes)

### Spark Processing
Configure Spark settings in `docker-compose.yml` under the `spark` service environment variables.

## Troubleshooting

### Build Issues
1. Ensure Java 21 is installed and `JAVA_HOME` is set
2. Run `./gradlew clean build` to rebuild from scratch
3. Check for network connectivity if dependencies fail to download

### Docker Issues
1. Ensure Docker is running and you have enough resources allocated
2. Run `docker-compose down -v` to clean up volumes if needed
3. Check port conflicts (9092, 8086, 3000, etc.)

### API Key Issues
1. Verify your API keys are correctly set in the `.env` file
2. Check API rate limits and quotas
3. Monitor logs for API error messages

### Service Startup Issues
```bash
# Check service status
docker-compose ps

# Restart specific service
docker-compose restart stream

# Rebuild and restart
docker-compose up -d --build stream
```

## Development

### Running Services Locally
You can run services outside Docker for development:

```bash
# Start only infrastructure
docker-compose up -d zookeeper kafka influxdb cassandra

# Run stream service locally
cd stream
../gradlew bootRun

# Run API service locally
cd api
../gradlew bootRun
```

### Adding New Data Sources
1. Add API configuration to `application.yml`
2. Create data models in the appropriate service
3. Add collection logic with `@Scheduled` methods
4. Update Kafka topic routing

### Custom Indicators
1. Add calculation logic in the Spark processor
2. Define new Kafka topics if needed
3. Update InfluxDB schema
4. Create Grafana dashboards

## API Documentation

The API service provides REST endpoints for accessing processed data:

- `GET /api/stocks/{symbol}` - Get current stock data
- `GET /api/volatility` - Get volatility metrics
- `GET /api/indicators` - Get economic indicators
- `GET /api/risk/{symbol}` - Get risk analysis for a symbol

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the troubleshooting section above
2. Review service logs for error messages
3. Create an issue in the repository with detailed information