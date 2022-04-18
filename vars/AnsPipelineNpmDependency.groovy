#!/usr/bin/groovy

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'java-slaves'
        }
        environment {
            // Identifiant du serveur de l'artifactory 
            artifactoryServerId = "${env.ARTIFACTORY_SERVER_ID}"
            // Credentials artifactory 
            ARTIFACTORY_CREDENTIALS_ID = credentials('jenkins-artifactory-account')
            // Destinataires du mail de notification
            mailList = "${env.MAIL_TMA}"
            // Node Version
            NODE_HOME = "node-v12.14.1-linux-x64"
        }
        stages {
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
                    }
                }
            }

            stage ('Npm package and ng build') {
                steps {
                    script {
                    // Get node from artifactory
                    // Install npm and set registry
                    // Build package
                    sh '''wget http://[IP]/artifactory/nodejs-cache/v16.13.0/node-v16.13.0-linux-x64.tar.gz
                        tar xvzf node-v16.13.0-linux-x64.tar.gz
                        BASE_PATH=$PWD
                        export PATH=$PATH:$BASE_PATH/node-v16.13.0-linux-x64/bin     
                        echo "PATH=$PATH"
                        npm config set registry http://[IP]/artifactory/api/npm/npm
                        npm install npm
                        npm install -g @angular/cli@13.2.1
                        npm install --save-dev @angular/common@^13.0.0
                        npm install --save-dev @angular/core@^13.0.0
                        npm install --legacy-peer-deps
                        ng build ngx-ans-design-system --prod
                        cd dist/ngx-ans-design-system/
                        npm pack
                        '''
                    }
                }
            }
            
            stage("Push to artifactory") {
                agent none
                steps {
                    script {
                        configFileProvider([
                            configFile(
                                fileId: 'org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig1427306179475', 
                                targetLocation: 'ASIPGlobalSettings.xml', 
                                variable: 'ASIPGlobalSettings'
                            ),
                            configFile(
                                fileId: 'org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig1452787041167', 
                                targetLocation: 'ASIPProfilesSettings.xml', 
                                variable: 'ASIPProfilesSettings'
                            )
                        ]) {
                            if(pipelineParams?.artifactoryPath) {
                                def server = Artifactory.server "${artifactoryServerId}"
                                artifactoryPrimaryPath = "npm"
                                artifactoryPath = pipelineParams?.artifactoryPath
                                echo "Le path Artifactory target est ${artifactoryPrimaryPath}/${artifactoryPath}/"

                                def uploadSpec = """{
                                    "files": [{
                                            "pattern": "dist/ngx-ans-design-system/ngx-ans-design-system-*.tgz",
                                            "target": "${artifactoryPrimaryPath}/${artifactoryPath}/-/"
                                            }
                                        ]
                                    }"""
                                server.upload(uploadSpec)
                                echo "Fin de l'upload"
                                } else {          
                                    echo "Warning ! Mettez dans les pipelineParams un artifactoryPath : artifactory/npm-local/artifactoryPath/xx.tgz"            
                                }
                        }
                    }
                }
            }

            // Ajouter un lien à l'artifactory release promotion sur l'interface Jenkins
            stage ('Add interactive promotion') {
                steps {
                    script {
                        targetRepo = "npm-local"
                        artifactoryConfiguration(artifactoryServerId, targetRepo)
                    }  
                }
            } 
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