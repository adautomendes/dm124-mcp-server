def petstoreRefRepo = 'https://github.com/adautomendes/petstore-ref-project.git'
def petstoreRefDir = 'petstore-ref-project'
def petstoreRefProjects = [
    'auth',
    'monitor',
    'core'
]

def petstoreMcpServerRepo = 'https://github.com/adautomendes/petstore-mcp-server.git'
def petstoreMcpServerDir = 'petstore-mcp-server'

def dockerhubUsername = 'adautomendes'
def dockerhubImagePrefix = 'petstore'

pipeline {
    agent any

    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }
        stage('Clone repositories') {
            parallel {
                stage('Petstore MCP Server') {
                    steps {
                        script {
                            sh "git clone ${petstoreMcpServerRepo} ${petstoreMcpServerDir}"
                        }
                    }
                }
                stage('Petstore Reference') {
                    steps {
                        script {
                            sh "git clone ${petstoreRefRepo} ${petstoreRefDir}"
                        }
                    }
                }
            }
        }
        stage('Docker build') {
            steps {
                script {
                    def parallelStages = petstoreRefProjects.collectEntries { project ->
                        ["${project} image build": {
                            dir("${petstoreRefDir}/${project}") {
                                sh "docker build -t ${dockerhubUsername}/${dockerhubImagePrefix}-${project}:latest ."
                            }
                        }]
                    }

                    parallelStages += [
                        "petstore-mcp-server image build": {
                            dir("${petstoreMcpServerDir}") {
                                sh "docker build -t ${dockerhubUsername}/petstore-mcp-server:latest -f ./docker/Dockerfile ."
                            }
                        }
                    ]

                    parallel parallelStages
                }
            }
        }
        stage('Docker Tagging and Pushing') {
            steps {
                script {
                    def parallelStages = petstoreRefProjects.collectEntries { project ->
                        ["${project} push": {
                            dir("${petstoreRefDir}/${project}") {
                                script {
                                    def version = sh(script: "node -p \"require('./package.json').version\"", returnStdout: true).trim()

                                    println "Project '${project}' version is: ${version}"

                                    sh "docker tag ${dockerhubUsername}/${dockerhubImagePrefix}-${project}:latest ${dockerhubUsername}/${dockerhubImagePrefix}-${project}:${version}"

                                    withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKERHUB_USERNAME', passwordVariable: 'DOCKERHUB_PASSWORD')]) {
                                        sh "echo $DOCKERHUB_PASSWORD | docker login -u $DOCKERHUB_USERNAME --password-stdin"
                                        sh "docker push ${dockerhubUsername}/${dockerhubImagePrefix}-${project}:latest"
                                        sh "docker push ${dockerhubUsername}/${dockerhubImagePrefix}-${project}:${version}"
                                    }
                                }
                            }
                        }]
                    }

                    parallelStages += [
                        "petstore-mcp-server push": {
                            dir("${petstoreMcpServerDir}") {
                                script {
                                    def version = sh(script: "mvn help:evaluate -f java/pom.xml -Dexpression=project.version -q -DforceStdout", returnStdout: true).trim()

                                    println "Petstore MCP Server version is: ${version}"

                                    sh "docker tag ${dockerhubUsername}/petstore-mcp-server:latest ${dockerhubUsername}/petstore-mcp-server:${version}"

                                    withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKERHUB_USERNAME', passwordVariable: 'DOCKERHUB_PASSWORD')]) {
                                        sh "echo $DOCKERHUB_PASSWORD | docker login -u $DOCKERHUB_USERNAME --password-stdin"
                                        sh "docker push ${dockerhubUsername}/petstore-mcp-server:latest"
                                        sh "docker push ${dockerhubUsername}/petstore-mcp-server:${version}"
                                    }
                                }
                            }
                        }
                    ]

                    parallel parallelStages
                }
            }
        }
    }
}