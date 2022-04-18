#!/usr/bin/groovy

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'java-slaves'
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        }
        environment {
            // Version utilisée de SonarQube 
            sonarQubeEnv = "${env.SONARQUBE_VERSION}"
            // Credentials SVN
            svnCredentialsId = "${env.SVN_CREDENTIALS_TMA}"
            // Identifiant du serveur de l'artifactory 
            artifactoryServerId = "${env.ARTIFACTORY_SERVER_ID}"
            // Credentials artifactory 
            ARTIFACTORY_CREDENTIALS_ID = credentials('jenkins-artifactory-account')
            // Destinataires du mail de notification
            mailList = "${env.MAIL_TMA}"
        }
        stages {
            // Lecture de l'input Jenkins qui décrit version à publier ou par défaut la snapshot actuelle
            stage ('Initialize') {
                steps {
                    script {
                        // On override le mail si besoin (utile pour les acteurs hors TMA)
                        if(pipelineParams?.mailList) {
                            if(pipelineParams?.mailList == "TRA") {
                                mailList = "${env.MAIL_TRA}"
                                echo "Before release, mailList is changed from ${env.MAIL_TMA} to ${mailList}" 
                            }
                            else {
                                mailList = pipelineParams?.mailList
                                echo "Before release, mailList is changed from ${env.MAIL_TMA} to ${mailList}" 
                            }
                        }
                        
                        // Récupération de la nouvelle version release et snapshot ?

                    }
                }
            }

            stage('SonarQube analysis') {
                steps {
                    script {
                        def scannerHome = tool 'sonar-scanner';
                        withSonarQubeEnv(sonarQubeEnv) {
                        sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=ivan -Dsonar.projectname=ivan -Dsonar.projectVersion=${env.BRANCH_NAME} -Dsonar.branch.name=master -Dsonar.source=apps -Dsonar.python.version=3.7"
                        }
                    }
                }
            }

            //stage("Build") {
            //stage("Push to artifactory") {
            //stage ('Add interactive promotion') {

        }
		
        // Notification mail
        post {
            success {
                mail (
                    to: "${mailList}",
                    subject: "Success Build ${BUILD_TAG}",
                    body: "Le build ${BUILD_TAG} a été réalisé avec succès !"
                )
            }
            failure {
                mail (
                    to: "${mailList}",
                    subject: "Failure Build ${BUILD_TAG}",
                    body: "Le build ${BUILD_TAG} a échoué"
                )
            }
			unstable {
                mail(
					to: "${mailList}",
					subject: "Unstable Build ${BUILD_TAG}",
					body: "Le build ${BUILD_TAG} est instable"
                )
            }
            aborted {
                mail (
                    to: "${mailList}",
                    subject: "Aborted Build ${BUILD_TAG}",
                    body: "Le build ${BUILD_TAG} a échoué"
                )
            }
        }
    }
}