def petstoreRefRepo = 'https://github.com/adautomendes/petstore-ref-project.git'
def petstoreRefDir = 'petstore-ref-project'
def petstoreRefProjects = [
    'auth',
    'monitor',
    'core'
]
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
        stage('Clone DM124 Repository') {
            steps {
                script {
                    sh "git clone ${petstoreRefRepo} ${petstoreRefDir}"
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
                    parallel parallelStages
                }
            }
        }
    }
}