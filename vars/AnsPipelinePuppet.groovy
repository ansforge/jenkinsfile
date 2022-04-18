#!/usr/bin/groovy

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'puppet6'
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        }
        environment {
			// Credentials SVN
            svnCredentialsId = "${env.SVN_CREDENTIALS_TMA}"
            // Identifiant du serveur de l'artifactory 
            artifactoryServerId = "${env.ARTIFACTORY_SERVER_ID}"
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
                        // Récupération de la nouvelle version release et snapshot  
                        inputPuppetRelease()
                        echo "ArtifactoryPrimaryPath = ${env.artifactoryPrimaryPath}"
                    }
                }
            }

            stage("Quality check") {
                agent none
                steps {
                    script {  
                        sh '''for file in $(find . -iname '*.pp'); do
                            /opt/puppetlabs/bin/puppet parser validate --color false --render-as s --modulepath=modules $file || exit 1;
                            done;
                            find . -iname *.pp -exec /usr/local/puppetlabs/pdk/share/cache/ruby/2.5.0/gems/puppet-lint-2.3.6/bin/puppet-lint --no-autoloader_layout-check --no-80chars-check --no-class_inherits_from_params_class-check --log-format "%{path}:%{line}:%{check}:%{KIND}:%{message}" {} \\;'''
                    }
                }
            }

            stage ('Puppet linter') {
                steps {
                    script {
                        stage('Warning') {
                            warnings canComputeNew: false, canResolveRelativePaths: false, categoriesPattern: '', consoleParsers: [[parserName: 'Puppet-Lint']], defaultEncoding: '', excludePattern: '', healthy: '', includePattern: '', messagesPattern: '', unHealthy: ''
                        }
                    }
                }
            }

            stage("Build") {
                agent none
                steps {
                    script {
                        // The pdk build command performs a series of checks on your module and builds a tar.gz package so that you can upload your module to the Forge.
                        sh ''' pdk build . --force'''

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
                                artifactoryPath = pipelineParams?.artifactoryPath
                                
                                // artifactory primary path pour choisir le début du chemin sur Artifactory via input
                                if(env.artifactoryPrimaryPath) {
                                    artifactoryPrimaryPath = env.artifactoryPrimaryPath
                                } else {
                                    // par défaut 
                                    artifactoryPrimaryPath = "puppet-snapshots"
                                }
                                // Possibilité de mettre le début du chemin sur Artifactory directement dans les pipelineParams
                                if(pipelineParams?.artifactoryPrimaryPath) {
                                    artifactoryPrimaryPath = pipelineParams?.artifactoryPrimaryPath
                                }

                                def uploadSpec = """{
                                    "files": [{
                                                "pattern": "pkg/*.tar.gz",
                                                "target": "${artifactoryPrimaryPath}/${artifactoryPath}/"
                                            }
                                        ]
                                    }"""
                                server.upload(uploadSpec)
                            } else {          
                                echo "Warning ! Mettez dans les pipelineParams un artifactoryPath : artifactory/puppet-integration/artifactoryPath/xx.tar.gz"            
                            }
                        }
                    }
                }
            }
						
            // Ajouter un lien à l'artifactory release promotion sur l'interface Jenkins
            stage ('Add interactive promotion') {
                steps {
                    script {
                        targetRepo = "puppet-integration"
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