#!/bin/bash

echo "========================================"
echo "StockiFAI - Real-time Stock Monitor Setup"
echo "========================================"

echo
echo "Step 1: Creating environment file..."
if [ ! -f .env ]; then
    cp .env.example .env
    echo ".env file created from .env.example"
    echo "Please edit .env file to add your API keys:"
    echo "- ALPHAVANTAGE_API_KEY"
    echo "- FRED_API_KEY"
    echo
else
    echo ".env file already exists"
fi

echo "Step 2: Cleaning and building the project..."
./gradlew clean build

if [ $? -ne 0 ]; then
    echo "Build failed! Please check the error messages above."
    exit 1
fi

echo
echo "Step 3: Setting up Docker infrastructure..."
echo "Starting Kafka, InfluxDB, and Cassandra..."
docker-compose up -d zookeeper kafka influxdb cassandra

echo
echo "Waiting for services to be ready..."
sleep 30

echo
echo "Step 4: Creating Kafka topics..."
docker-compose exec kafka kafka-topics.sh --create --topic finnhub-ohlcv-data --bootstrap-server localhost:9093 --partitions 3 --replication-factor 1 || true
 docker-compose exec kafka kafka-topics.sh --create --topic finnhub-market-news-data --bootstrap-server localhost:9093 --partitions 3 --replication-factor 1 || true
 docker-compose exec kafka kafka-topics.sh --create --topic finnhub-company-news-data --bootstrap-server localhost:9093 --partitions 3 --replication-factor 1 || true
docker-compose exec kafka kafka-topics.sh --create --topic fred-economic-observations-data --bootstrap-server localhost:9093 --partitions 3 --replication-factor 1 || true

echo
echo "Step 5: Starting all services..."
docker-compose up -d

echo
echo "========================================"
echo "Setup Complete!"
echo "========================================"
echo
echo "Services will be available at:"
echo "- Stream Service (Data Collection): http://localhost:8081"
echo "- API Service: http://localhost:8000"
echo "- InfluxDB UI: http://localhost:8086"
echo "- Grafana: http://localhost:3000 (admin/admin)"
echo "- Prometheus: http://localhost:9090"
echo
echo "To monitor logs:"
echo "docker-compose logs -f [service_name]"
echo
echo "To stop all services:"
echo "docker-compose down"
echo