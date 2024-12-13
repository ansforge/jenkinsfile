#!/usr/bin/groovy

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'java-slaves'
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
        }
        environment {
            // Version utilisée de SonarQube
            sonarQubeEnv = "${env.SONARQUBE_VERSION}"
            // Credentials 
            svnCredentialsId = "${env.SVN_CREDENTIALS_TMA}"
            // Identifiant du serveur de l'artifactory
            artifactoryServerId = "${env.ARTIFACTORY_SERVER_ID}"
            // Credentials artifactory
            ARTIFACTORY_CREDENTIALS_ID = credentials('jenkins-artifactory-account')
            // Destinataires du mail de notification
            mailList = "${env.MAIL_TMA}"
        }
        stages {
            stage('Initialize') {
                steps {
                    script {
                        initialization(pipelineParams?.svnCredentialsId, pipelineParams?.cleanWSoption, pipelineParams?.mailList,
                                pipelineParams?.skipTest, pipelineParams?.directoryPath, pipelineParams?.frontendPath,
                                pipelineParams?.mvnPerformOpts, pipelineParams?.pathOfDockerfile, pipelineParams?.projectName, pipelineParams?.autosemver)
                    }
                }
            }

            stage('Chemins Artifactory') {
                options {
                    timeout(time: 30, unit: 'SECONDS')
                }
                steps {
                    script {
                            try {
                                if (pipelineParams?.artifactoryPath) {
                                    artifactoryPrimaryPath = 'asip-snapshots'
                                    artifactoryPath = pipelineParams?.artifactoryPath
                                    artifactoryPrimaryPath = input message: 'Par défaut asip-snapshots, vous pouvez choisir un autre repository', ok: 'Lancer le build !',
                                        parameters: [choice(name: 'ArtifactoryPrimaryPath', choices: ['asip-snapshots', 'asip-releases'], description: 'Path Artifactory')]
                                    echo artifactoryPrimaryPath : artifactoryPath
                                } else {
                                    echo 'Warning ! Mettez dans les pipelineParams un artifactoryPath : artifactory/puppet-releases/artifactoryPath/xx.tar.gz'
                                }
                            } catch (Throwable e) {
                                currentBuild.result = 'SUCCESS'
                            }
                    }
                }
            }

            stage('SonarQube analysis') {
                steps {
                    script {
                        if (pipelineParams?.anyLanguage && pipelineParams?.applicationName) {
                            def scannerHome = tool 'sonar-scanner'
                            withSonarQubeEnv(sonarQubeEnv) {
                                sh """
                            ${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${pipelineParams?.applicationName} -Dsonar.projectname=${pipelineParams?.applicationName} -Dsonar.projectVersion=\$version -Dsonar.exclusions=**/DST/*.xml -Dsonar.branch.name=${env.BRANCH_NAME} -Dsonar.source=apps
                        """
                            }
                        } else {
                            echo 'Warning ! Mettez dans les pipelineParams un applicationName et anyLanguage'
                        }
                    }
                }
            }
        }

        // Notification mail
        post {
            always {
                step([$class: 'Mailer',
                  notifyEveryUnstableBuild: true,
                  recipients: "${mailList}",
                  sendToIndividuals: true])
            }
        }
    }
}
