#!/usr/bin/groovy

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'java-slaves'
        }
        triggers {
            cron(env.BRANCH_NAME == 'feat/auto-update' ? '0 8 * * *' : '')
        }
        environment {
            // Version utilisée de SonarQube 
            sonarQubeEnv = "${env.SONARQUBE_VERSION}"
			// Credentials Git
            svnCredentialsId = "${env.SVN_CREDENTIALS_TMA}"
            // Identifiant du serveur de l'artifactory 
            artifactoryServerId = "${env.ARTIFACTORY_SERVER_ID}"
			// Credentials artifactory 
			ARTIFACTORY_CREDENTIALS_ID = credentials('jenkins-artifactory-account')
            // Destinataires du mail de notification
            mailList = "${env.MAIL_TMA}"            
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
            disableConcurrentBuilds() 
        }
        // Récupération des outils nécessaires au projet
        tools {
            maven "${env.MVN_39}"
            jdk "${env.JDK_17}"
        }
        stages {
            stage ('Initialize') {
                steps {
                    script {
                        initialization(pipelineParams?.svnCredentialsId, pipelineParams?.cleanWSoption, pipelineParams?.mailList, 
                                pipelineParams?.skipTest, pipelineParams?.directoryPath, pipelineParams?.frontendPath, 
                                pipelineParams?.mvnPerformOpts, pipelineParams?.pathOfDockerfile, pipelineParams?.projectName, pipelineParams?.autosemver)
						// Lecture de l'input Jenkins qui décrit la version à release ou par défaut la snapshot actuelle
                        if( env.BRANCH_NAME == "master" || env.BRANCH_NAME == "main" || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/ ) {
                            if (pipelineParams?.autosemver) {
                                inputAutoRelease()
                            } else {
                                inputSnapshotRelease()
                            }
                        } 
                        echo "NewSnapshotVersion ${env.newSnapshotVersion} - NewReleaseVersion ${env.newReleaseVersion}"
                    }
                }
            }

            // Auto update library
            stage ('Automatically autoupdate and increase version') {
                steps {
                    script {
                        // If job is triggered by cron, it means we will try to autoupdate
                        echo "La build cause est ${currentBuild.buildCauses} && cron build related ${currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause')}" 
                        // On lance l'autoupdate selon la branche et le déclenchement du build
                        if (pipelineParams?.autoUpdateBranchName && currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause')) {
                            autoUpdate(svnCredentialsId, mvnPerformOpts, pomArtifactId, mailList,  
                                pipelineParams?.applicationName, pipelineParams?.trivyParams, pipelineParams?.trivySeverity)
                        }
                    }  
                }
            }

            // Definir la repository de destination selon la version récupérée (Release, RC ou Snapshot)
            stage('Setting artifactory repository') {
                steps {
                    script {
                        targetVersion = env.newReleaseVersion ?: "${pomVersion}"
                        targetRepo = artifactRepository(targetVersion)
						echo "Target Repo ${targetRepo} & Target Version ${targetVersion}"
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

            stage('SonarQube analysis') {
                when {
                    expression { skipTest != "true" }
                }
                steps {
                    script {
                        dir(env.directoryPath){
                            if (pipelineParams?.isAngular) {
                                echo "On est en Angular"
                                sonarQubeAnalysis(sonarQubeEnv, "${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:0.8.8:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.8:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.scm.provider=git -Dsonar.sources=${frontendPath} -Dsonar.exclusions=${frontendPath}/coverage/**,${frontendPath}/dist/**,${frontendPath}/e2e/**,${frontendPath}/node_modules/**,${frontendPath}/node/**,**/*.spec.ts,**/*.conf.js,**/*.TestDataService.ts,frontend/src/assets/** -Dsonar.nodejs.executable=node-v18.15.0-linux-x64/bin/node -Dsonar.tests=${frontendPath} -Dsonar.test.inclusions=**/*.spec.ts -Dsonar.ts.tslint.configPath=${frontendPath}/tslint.json -Dsonar.ts.tslint.path=target/sonar/sonarts-bundle/node_modules/.bin/tslint -Dsonar.javascript.lcov.reportPaths=frontend/coverage/coverage-final.json  -Dsonar.dependencyCheck.htmlReportPath=./dependency-check-report.html")
                            } 
                            else {
                                echo "On est en Java"
                                sonarQubeAnalysis(sonarQubeEnv,"${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.8:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.scm.provider=git -Dsonar.java.binaries=target/classes -Dsonar.java.libraries=${env.HOME}/.m2/repository/org/projectlombok/lombok/**/*.jar -Dsonar.coverage.jacoco.xmlReportPaths=/target/site/jacoco/jacoco.xml  -Dsonar.dependencyCheck.htmlReportPath=./dependency-check-report.html")
                            } 
                        }
                    } 
                }
                // Publier les rapports des tests en cas de succès
                post {
                    success {
                        script{
                            if(!pipelineParams?.containsKey("isAngular")) {
                                junit allowEmptyResults: true, testResults: '**/surefire-reports*/*.xml'
                            }
                        }
                    }
                    failure {
                        script {
                            currentBuild.result = 'FAILURE'
                            error("Echec de l'analyse SonarQube")
                        }
                    }
                }
            }

            // Vérifier les Quality Gate avec un Timeout de 3 minutes
            stage("Quality Gate") {
                agent none
                when {
                    expression {(skipTest != "true" && env.newSnapshotVersion && env.newReleaseVersion && targetRepo == "asip-releases") || env.BRANCH_NAME == "master" || env.BRANCH_NAME == "main" }
                }
                steps {
                    script {
                        if(pipelineParams?.qualityGate == false) {
                            echo "Pas de vérification de la Quality gate Sonar" 
                            currentBuild.result = 'UNSTABLE'
                        } else {
                            echo "Vérification de la Quality gate Sonar" 
                            qualityGate(sonarQubeEnv)
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
                        dir(env.directoryPath){
                            if( env.BRANCH_NAME == "master" || env.BRANCH_NAME == "main" || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/ ) {                      
                                    mavenVersionReleaseArtifactory("${env.newSnapshotVersion}", "${env.newReleaseVersion}", "${svnCredentialsId}", "${targetRepo}", "${mvnPerformOpts}", "", "${env.directoryPath}", pipelineParams?.isDockerDaemon)
                                    targetRepo = artifactRepository(env.newSnapshotVersion)
                            }
                            else {
                                echo "Pas une branche stable : main, master, dev, release, bugfix ou hotfix"
                                error "La branche {env.BRANCH_NAME} ne doit pas être utilisée pour faire des releases. La branche doit etre master ou main ou dans des cas particuliers : bug, hotfix ou release."
                            }
                        }
                    }
                }
            }

            stage('Build and publish docker image with Docker Daemon and Scan with Trivy') {
                when {
                    expression {pipelineParams?.isDockerDaemon && pipelineParams?.applicationName}
                }
                steps {
                    script {
                        dir(env.directoryPath){
                            buildDockerImage("${env.newSnapshotVersion}", "${env.newReleaseVersion}", "${svnCredentialsId}", "${pipelineParams?.applicationName}", pipelineParams?.pathOfDockerfile, projectName, pipelineParams)
                        }
                    }
                }
            }

            stage('Autodeploy by changing puppet version') {
                when {
                    expression { pipelineParams?.jobToDeploy }
                }
                steps {
                    build job: "${pipelineParams.jobToDeploy}", parameters: [[$class: 'StringParameterValue', name: 'versionToDeploy', value: "${env.newReleaseVersion}"]]
                }
            }   
		
            stage('Build Snapshot'){
				steps{
					script{
                        dir(env.directoryPath){
                            // Set nouvelle version
                            mavenVersionSet(env.newSnapshotVersion)
                            // Construction de la snapshot et pousser sur Artifactory
                            buildSnapshot(artifactoryServerId, targetRepo, pipelineParams?.featureBranchSnapshot)
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
