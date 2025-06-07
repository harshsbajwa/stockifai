pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = 'localhost:5000'
        KUBECONFIG = '/var/lib/jenkins/.kube/config'
        ANSIBLE_HOST_KEY_CHECKING = 'False'
    }
    
    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['development', 'staging', 'production'],
            description: 'Select environment to deploy'
        )
        string(
            name: 'APP_VERSION',
            defaultValue: 'latest',
            description: 'Application version to deploy'
        )
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build Docker Images') {
            parallel {
                stage('Build API') {
                    steps {
                        script {
                            def apiImage = docker.build("stockifai/api:${env.BUILD_NUMBER}", "./api")
                            apiImage.push()
                            apiImage.push("latest")
                        }
                    }
                }
                stage('Build Stream') {
                    steps {
                        script {
                            def streamImage = docker.build("stockifai/stream:${env.BUILD_NUMBER}", "./stream")
                            streamImage.push()
                            streamImage.push("latest")
                        }
                    }
                }
                stage('Build Spark') {
                    steps {
                        script {
                            def sparkImage = docker.build("stockifai/spark:${env.BUILD_NUMBER}", "./spark")
                            sparkImage.push()
                            sparkImage.push("latest")
                        }
                    }
                }
            }
        }
        
        stage('Test') {
            steps {
                echo 'Running tests...'
                sh './gradlew clean test --no-daemon'
            }
        }
        
        stage('Deploy Infrastructure') {
            steps {
                dir('infrastructure/terraform') {
                    sh 'terraform init'
                    sh 'terraform plan'
                    sh 'terraform apply -auto-approve'
                }
            }
        }
        
        stage('Deploy Application') {
            steps {
                dir('infrastructure/ansible') {
                    sh """
                        ansible-playbook playbooks/deploy-stockifai.yml \
                        -i inventories/local.yml \
                        -e app_version=${params.APP_VERSION} \
                        -e environment=${params.ENVIRONMENT}
                    """
                }
            }
        }
        
        stage('Verify Deployment') {
            steps {
                sh 'kubectl get pods -n stockifai'
                sh 'kubectl get services -n stockifai'
                sh 'kubectl get ingress -n stockifai'
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
        success {
            echo 'Deployment completed successfully!'
        }
        failure {
            echo 'Deployment failed!'
        }
    }
}
