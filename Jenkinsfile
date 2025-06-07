pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'localhost:5000'
        KUBECONFIG = "/home/harsh/.kube/config"
        APP_VERSION = "build-${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Building version: ${APP_VERSION}"
            }
        }

        stage('Build & Push Images') {
            steps {
                script {
                    def apiImage = docker.build("stockifai/api:${APP_VERSION}", "--build-arg BUILDKIT_INLINE_CACHE=1 -f api/Dockerfile .")
                    apiImage.tag("${DOCKER_REGISTRY}/stockifai/api:${APP_VERSION}")
                    apiImage.tag("${DOCKER_REGISTRY}/stockifai/api:latest")
                    apiImage.push("${DOCKER_REGISTRY}/stockifai/api:${APP_VERSION}")
                    apiImage.push("${DOCKER_REGISTRY}/stockifai/api:latest")

                    def streamImage = docker.build("stockifai/stream:${APP_VERSION}", "--build-arg BUILDKIT_INLINE_CACHE=1 -f stream/Dockerfile .")
                    streamImage.tag("${DOCKER_REGISTRY}/stockifai/stream:${APP_VERSION}")
                    streamImage.tag("${DOCKER_REGISTRY}/stockifai/stream:latest")
                    streamImage.push("${DOCKER_REGISTRY}/stockifai/stream:${APP_VERSION}")
                    streamImage.push("${DOCKER_REGISTRY}/stockifai/stream:latest")

                    def sparkImage = docker.build("stockifai/spark:${APP_VERSION}", "--build-arg BUILDKIT_INLINE_CACHE=1 -f spark/Dockerfile .")
                    sparkImage.tag("${DOCKER_REGISTRY}/stockifai/spark:${APP_VERSION}")
                    sparkImage.tag("${DOCKER_REGISTRY}/stockifai/spark:latest")
                    sparkImage.push("${DOCKER_REGISTRY}/stockifai/spark:${APP_VERSION}")
                    sparkImage.push("${DOCKER_REGISTRY}/stockifai/spark:latest")
                }
            }
        }

        stage('Test') {
            steps {
                echo 'Running tests...'
                sh './gradlew clean test --no-daemon'
            }
        }

        stage('Deploy to K3s') {
            steps {
                sh """
                sed -i 's|image: .*stockifai/api:.*|image: ${DOCKER_REGISTRY}/stockifai/api:${APP_VERSION}|g' infrastructure/k8s/stockifai-api.yaml
                sed -i 's|image: .*stockifai/stream:.*|image: ${DOCKER_REGISTRY}/stockifai/stream:${APP_VERSION}|g' infrastructure/k8s/stockifai-stream.yaml
                sed -i 's|image: .*stockifai/spark:.*|image: ${DOCKER_REGISTRY}/stockifai/spark:${APP_VERSION}|g' infrastructure/k8s/stockifai-spark.yaml
                """
                
                sh 'kubectl apply -f infrastructure/k8s/'
            }
        }

        stage('Verify Deployment') {
            steps {
                sh 'sleep 30'
                sh 'kubectl get pods -n stockifai'
                sh 'kubectl get ingress -n stockifai'
                sh 'curl -H "Host: api.stockifai.amneet.me" http://192.168.0.31/api/v1/health'
            }
        }
    }

    post {
        always {
            sh "git checkout -- infrastructure/k8s/"
            cleanWs()
        }
    }
}
