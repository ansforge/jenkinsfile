#!/usr/bin/groovy

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'java-slaves'
        }
        triggers {
            cron(env.BRANCH_NAME == 'branches/auto-update' ? '0 8 * * *' : '')
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
			// Récupération de nom de l'artifact et de la version depuis le pom à l'aide du Pipeline Utility Steps plugin
            pomVersion = readMavenPom().getVersion()
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            disableConcurrentBuilds() 
        }
        // Récupération des outils nécessaires au projet
        tools {
            maven "${env.MVN_354}"
            jdk "${env.JDK_18}"
        }
        stages {
            // Lecture de l'input Jenkins qui décrit version à publier ou par défaut la snapshot actuelle
            stage ('Initialize') {
                steps {
                    script {
                        // Changing Credentials
                        if(pipelineParams?.svnCredentialsId) {
                            if (pipelineParams?.svnCredentialsId == "TRA") {
                                svnCredentialsId = "${env.SVN_CREDENTIALS_TRA}"
                                echo "Before Maven release, svnCredentialsId is changed from ${env.SVN_CREDENTIALS_TMA} to ${svnCredentialsId}"  
                            }
                            else {
                                svnCredentialsId =  pipelineParams?.svnCredentialsId
                                echo "Before Maven release, svnCredentialsId is changed from ${env.SVN_CREDENTIALS_TMA} to ${svnCredentialsId}" 
                            }
                        }
                        // Si le dernier commit est lié au maven release plugin, pas besoin de rebuild l'application sauf pour l'autoupdate
                        withCredentials([usernamePassword(credentialsId: "${svnCredentialsId}", passwordVariable: 'PASSWORD_VAR', usernameVariable: 'USERNAME_VAR')]) {
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

                            // Git ou SVN
                            if(pipelineParams?.git) {
                                git = "true"
                                echo "Le projet est sous GIT." 
                                }
                            else {
                                git = "false"
                                echo "Le projet est sous SVN."
                            }

                            // Quel est le dernier log de commit pour Git ou SVN ?
                            if (pipelineParams?.git) {
                                lastCommit = sh (
                                    script: 'git log -1',
                                    returnStdout: true
                                ).trim()
                                echo "lastCommit: ${lastCommit}"
                            } else {
                                lastCommit = sh (
                                    script: 'svn log -l 1 --username ${USERNAME_VAR} --password ${PASSWORD_VAR} --no-auth-cache',
                                    returnStdout: true
                                ).trim()
                                echo "lastCommit: ${lastCommit}"
                            }

                            // If maven plugin, we do nothing except if it's a cron task for autoupdate
							if ( lastCommit.contains("maven-release-plugin") && !currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause')) {
								echo "WARNING : Attention, le dernier log contient [maven-release-plugin] et donc on ne relance pas de job pour éviter les doublons." 
								currentBuild.getRawBuild().getExecutor().interrupt(Result.SUCCESS)
								sleep(3)   // Interrupt is not blocking and does not take effect immediately.
						    }	else {
								echo "Last commit was a developer commit, we can do something"
							}
								
                            }
                        }

                        // Option pour nettoyer le workspace et refaire le checkout des sources (positioner à "true" pour l'activer)
                        if(pipelineParams?.cleanWSoption) {
                            echo "Clean Workspace : OK"
                            cleanWs()
                            checkout scm
                        }
					    else {
                            echo "Not Clean Workspace"
                        }

                        // On override le mail si besoin (utiles pour les acteurs hors TMA)
                        if(pipelineParams?.mailList) {
                            if(pipelineParams?.mailList == "TRA") {
                                mailList = "${env.MAIL_TRA}"
                                echo "Before Maven release, mailList is changed from ${env.MAIL_TMA} to ${mailList}" 
                            }
                            else {
                                mailList = pipelineParams?.mailList
                                echo "Before Maven release, mailList is changed from ${env.MAIL_TMA} to ${mailList}" 
                            }
                        }

                        // SkipTests
                        if(pipelineParams?.skipTest) {
                            skipTest = pipelineParams?.skipTest
                            echo "Skiptest is ${skipTest}" 
                        }   else {
                            skipTest = "false"
                            echo "Skiptest is ${skipTest}"  
                        }

                        // Path Coverage pour les rapports Sonar
                        if(pipelineParams?.pathCoverage) {
                            pathCoverage = pipelineParams?.pathCoverage
                            echo "Path coverage for jacoco report is changed to ${pathCoverage}" 
                        }      
                        else {
                            pathCoverage = ""
                            echo "Path coverage for jacoco report is changed to ${pathCoverage}" 
                        }

						// Récupération de la nouvelle version release et snapshot  
                        inputSnapshotRelease()
                        echo "NewSnapshotVersion ${env.newSnapshotVersion} - NewReleaseVersion ${env.newReleaseVersion}"
                    }
                }
            }

			// Definir la repository de destination selon la version récupérée (Release, RC ou Snapshot)
            stage('Setting artifactory repository') {
                steps {
                    script {
                        targetVersion = env.newReleaseVersion ?: "${pomVersion}"
						echo "Target Version ${targetVersion}"
                        targetRepo = artifactRepository(targetVersion)
						echo "Target Repo ${targetRepo}"
	                }
				}
			}

            // Lancer l'analyse SonarQube et packager l'application
            stage('SonarQube analysis') {
                when {
                    expression { skipTest != "true" }
                }
                steps {
                    script {
                        if(pipelineParams?.haveFrontModule) {
                            mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                            sonarQubeAnalysis(sonarQubeEnv, "${mvnPerformOpts} package org.sonarsource.scanner.maven:sonar-maven-plugin:3.5.0.1254:sonar org.jacoco:jacoco-maven-plugin:prepare-agent package org.jacoco:jacoco-maven-plugin:report org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.typescript.node=node-v8.12.0-linux-x64/bin/nodejs -Dsonar.nodejs.executable=node-v8.12.0-linux-x64/bin/node -DdataDirectory=/var/tmp/dependency-check-data/ -DcentralAnalyzerEnabled=false -Dformat=XML -DossindexAnalyzerEnabled=false -DautoUpdate=false -DretireJsAnalyzerEnabled=false")
                        }
                        else {
                            mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                            sonarQubeAnalysis(sonarQubeEnv,"${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.4:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.6.0.1398:sonar -Dsonar.coverage.jacoco.xmlReportPaths=/target/site/jacoco/jacoco.xml")
                        }
                    }
                }
                // Publier les rapports des tests en cas de succès
                post {
                    success {
                        junit allowEmptyResults: true, testResults: '**/surefire-reports*/*.xml'
                    }
                    failure {
                        script {
                            currentBuild.result = 'ABORTED'
                            error("Echec de l'analyse SonarQube")
                        }
                    }
                }
            }

            // Vérifier les dépendances du projet
            stage('OWASP Check Dependency') {
                steps {
                    script {
                        owaspCheckDependency()
                    }
                }
                post {
                    always {
                        dependencyCheckPublisher pattern: 'dependency-check-report.xml'
                    }
                }
            }

            // Vérifier les Quality Gate avec un Timeout de 3 minutes 
            stage("Quality Gate") {
                agent none
                when {
                    expression { skipTest != "true" }
                }
                steps {
                    script {
                        // QualityGate Bloquant
                        if(pipelineParams?.qualityGate) {
                            echo "Quality gate can cause failure here" 
                            qualityGate()
                        }
                    }
                }
            }

			// Commande Maven qui permet de modifier le pom du projet avec la nouvelle version récupérée
            stage('Maven release:clean prepare perform') {
                when {
                    expression { env.newSnapshotVersion && env.newReleaseVersion && targetRepo == "asip-releases" }
                }
                steps {
                    script {
                        mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                        mavenVersionReleaseArtifactory("${env.newSnapshotVersion}", "${env.newReleaseVersion}", "${svnCredentialsId}", "${targetRepo}", "${mvnPerformOpts}", "${git}", "") 
                        targetRepo = artifactRepository(env.newSnapshotVersion)
                    }
                }
            }

            stage('Build Snapshot or RC'){
				steps{
					script{
						mavenVersionSet(env.newSnapshotVersion)
						
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
							// Init Maven Deployer
							rtMavenDeployer (
								id: "MAVEN_DEPLOYER",
								serverId: "${artifactoryServerId}",
								releaseRepo: "${targetRepo}",
								snapshotRepo: "${targetRepo}"
							)
							
							//Init Maven Resolver
							rtMavenResolver(
								id: "MAVEN_RESOLVER",
								serverId: "${artifactoryServerId}",
							)
							
							// Maven create release
							rtMavenRun (
								pom: 'pom.xml',
								goals: 'clean install -s $ASIPProfilesSettings -gs $ASIPGlobalSettings -DskipTests=true',
								deployerId: "MAVEN_DEPLOYER",
								resolverId: "MAVEN_RESOLVER"
							)
							
							// Publish Build Info
							rtPublishBuildInfo(
								serverId: "${artifactoryServerId}"
							)
						}
					}
				}
			}
		
            // Ajouter un lien à l'artifactory release promotion sur l'interface Jenkins
            stage ('Add interactive promotion') {
                steps {
                    script {
                        artifactoryConfiguration(artifactoryServerId, targetRepo)
                    }  
                }
            }
            			
			// Suppression du workspace Jenkins en cas de Release Candidate
            stage ('Clean Workspace') {
                when {
                    expression { targetRepo == "asip-releases-candidate" }
                }
				steps {
                    script {
						cleanWs()
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
