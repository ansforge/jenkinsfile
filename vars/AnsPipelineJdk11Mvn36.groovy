#!/usr/bin/groovy

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'java-slaves'
        }
        triggers {
            cron(env.BRANCH_NAME == 'auto-update' ? '0 8 * * *' : '')
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
            pomArtifactId = readMavenPom().getArtifactId()
            // Url Git for autoupdate
            // urlGitAutoUpdate = ""
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            disableConcurrentBuilds() 
        }
        // Récupération des outils nécessaires au projet
        tools {
            maven "${env.MVN_363}"
            jdk "${env.JDK_11}"
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
								echo "Le dernier commit est un commit de développeur. Le reste des actions peut continuer."
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

            // Auto update library
            stage ('Automatically autoupdate and increase version') {
                steps {
                    script {
                        // TODO : externaliser cette étape dans une librairie partagée

                        // If job is triggered by cron, it means we will try to autoupdate
                        echo "Get build cause ${currentBuild.buildCauses}" 
						echo "Is cron build related ${currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause')}"

                        // On lance l'autoupdate selon la branche et le déclenchement du build
                        if (pipelineParams?.autoUpdateBranchName && currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause')) {
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

                                    // MD5_BEFORE
                                    MD5_BEFORE = sh (
                                        script: 'md5sum pom.xml',
                                        returnStdout: true
                                    ).trim()
                                    echo "MD5_BEFORE: ${MD5_BEFORE}"
                                    echo "Play Mvn Update Parent"
                                    
                                    sh '''md5_old=`md5sum pom.xml |awk '{print $1}'`'''
                                    
                                    sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings versions:update-parent -Ddist=jar"
                                    
                                    sh '''md5_new=`md5sum pom.xml |awk '{print $1}'`'''
                                    sh '''if [[ \$$md5_old != \$$md5_new ]]; then
                                    echo "Updating spring boot dependency"
                                    else
                                    echo "No changes ${md5_old} = \$md5_new"
                                    fi'''

                                    // MD5_AFTER
                                    MD5_AFTER = sh (
                                        script: 'md5sum pom.xml',
                                        returnStdout: true
                                    ).trim()
                                    echo "MD5_AFTER: ${MD5_AFTER}"

                                    if ( MD5_BEFORE == MD5_AFTER ) {
                                        // Rien ne se passe
                                        echo "MD5_BEFORE == MD5_AFTER"
                                    }
                                    else {
                                        // On crée une nouvelle version pour mettre à jour la librairie.
                                        mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                                        targetRepo = 'asip-releases'

                                        // Running Check dependency
                                        owaspCheckDependency()
                                        dependencyCheckPublisher pattern: 'dependency-check-report.xml'

                                        env.urlGitAutoUpdate = "${env.GIT_URL}".substring(7)

                                        // Git add modified pom.xml and then push it to auto-update branch so we don't have discrepancies between local and remote
                                        sh '''
                                        git add pom.xml
                                        git commit -am "Spring boot dependency updated"
                                        git push --force "http://${USERNAME_VAR}:${PASSWORD_VAR}@${urlGitAutoUpdate}"
                                        '''

                                        // New release
                                        mavenVersionReleaseArtifactory("${env.newSnapshotVersion}", "${env.newReleaseVersion}", "${svnCredentialsId}", "${targetRepo}", "${mvnPerformOpts}", "${git}", "auto-update")

                                        if(pipelineParams?.isDocker && pipelineParams?.applicationName) {
                                            if( env.newSnapshotVersion != env.newReleaseVersion ) {
                                                sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings versions:set -DnewVersion=${env.newReleaseVersion} jib:build -Djib.to.auth.username=${ARTIFACTORY_CREDENTIALS_ID_USR} -Djib.to.auth.password=${ARTIFACTORY_CREDENTIALS_ID_PSW} -DsendCredentialsOverHttp=true -Djib.allowInsecureRegistries=true -Djib.to.tags=${env.newReleaseVersion},latest -Djib.to.image=[IP]/ans/${pipelineParams?.applicationName}"
                                            }
                                            else {
                                                sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings versions:set -DnewVersion=${env.newReleaseVersion} jib:build -Djib.to.auth.username=${ARTIFACTORY_CREDENTIALS_ID_USR} -Djib.to.auth.password=${ARTIFACTORY_CREDENTIALS_ID_PSW} -DsendCredentialsOverHttp=true -Djib.allowInsecureRegistries=true -Djib.to.tags=${env.newReleaseVersion},latest -Djib.to.image=[IP]/ans/${pipelineParams?.applicationName}"
                                            }
                                        }

                                        // Mail ssi md5 différents : build ok or not ok
                                        emailext (
                                            subject: "Autoupdate Build ${BUILD_TAG} [${env.BUILD_NUMBER}]'",
                                            body: """Le build [${env.BUILD_NUMBER}] ${BUILD_TAG} a été réalisé car le md5 précédent ${MD5_BEFORE} =/= ${MD5_AFTER} 
                                            Version release : ${env.newReleaseVersion}
                                            Version snapshot : ${env.newSnapshotVersion}
                                            Url du git : ${urlGitAutoUpdate}
                                            Disponible sur Artifactory dans ${targetRepo} avec comme ArtifactId : ${pomArtifactId}
                                            Job disponible ici : ${env.BUILD_URL} """,
                                            to: "${mailList}"
                                        )
                                    }

                                // End the build because we want only to autoupdate 
                                currentBuild.getRawBuild().getExecutor().interrupt(Result.SUCCESS)
                                sleep(3)   // Interrupt is not blocking and does not take effect immediately.
                                }
                            }
                        }
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


            stage('SonarQube analysis') {
                when {
                    expression { skipTest != "true" }
                }
                steps {
                    script {
                        // Si Angular et Git et latest Node
                        if (pipelineParams?.isAngular && pipelineParams?.git && pipelineParams?.latestNode) {
                            echo "On est en Git Angular et latestNode"
                            mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                            sonarQubeAnalysis(sonarQubeEnv, "${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:0.8.4:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.4:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.scm.provider=git -Dsonar.sources=src/main/frontend -Dsonar.exclusions=src/main/frontend/coverage/${pathCoverage}/**,src/main/frontend/dist/**,src/main/frontend/e2e/**,src/main/frontend/node_modules/**,**/*.spec.ts,**/*.conf.js,**/*.TestDataService.ts -Dsonar.typescript.node=node-v16.13.0-linux-x64/bin/node -Dsonar.nodejs.executable=node-v16.13.0-linux-x64/bin/node -Dsonar.tests=src/main/frontend -Dsonar.test.inclusions=**/*.spec.ts -Dsonar.ts.tslint.configPath=src/main/frontend/tslint.json -Dsonar.ts.tslint.path=target/sonar/sonarts-bundle/node_modules/.bin/tslint -Dsonar.typescript.lcov.reportPaths=target/coverage/lcov.info")
                        } // Si Angular et Git
                        else if (pipelineParams?.isAngular && pipelineParams?.git) {
                            echo "On est en Git Angular sans latestNode"
                            mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                            sonarQubeAnalysis(sonarQubeEnv, "${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:0.8.4:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.4:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.scm.provider=git -Dsonar.sources=src/main/frontend -Dsonar.exclusions=src/main/frontend/coverage/**,src/main/frontend/dist/**,src/main/frontend/e2e/**,src/main/frontend/node_modules/**,**/*.spec.ts,**/*.conf.js,**/*.TestDataService.ts -Dsonar.typescript.node=node-v12.14.1-linux-x64/bin/node -Dsonar.nodejs.executable=node-v12.14.1-linux-x64/bin/node -Dsonar.tests=src/main/frontend -Dsonar.test.inclusions=**/*.spec.ts -Dsonar.ts.tslint.configPath=src/main/frontend/tslint.json -Dsonar.ts.tslint.path=target/sonar/sonarts-bundle/node_modules/.bin/tslint -Dsonar.typescript.lcov.reportPaths=target/coverage/lcov.info")
                        } // Si Angular et SVN
                        else if (pipelineParams?.isAngular) {
                            echo "On est en SVN et Angular"
                            mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                            sonarQubeAnalysis(sonarQubeEnv, "${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:0.8.4:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.4:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.6.0.1398:sonar -Dsonar.sources=src/main/frontend -Dsonar.exclusions=src/main/frontend/coverage/${pathCoverage}/**,src/main/frontend/dist/**,src/main/frontend/e2e/**,src/main/frontend/node_modules/**,**/*.spec.ts,**/*.conf.js,**/*.TestDataService.ts -Dsonar.typescript.node=node-v12.14.1-linux-x64/bin/node -Dsonar.nodejs.executable=node-v12.14.1-linux-x64/bin/node -Dsonar.tests=src/main/frontend -Dsonar.test.inclusions=**/*.spec.ts -Dsonar.ts.tslint.configPath=src/main/frontend/tslint.json -Dsonar.ts.tslint.path=target/sonar/sonarts-bundle/node_modules/.bin/tslint -Dsonar.typescript.lcov.reportPaths=target/coverage/lcov.info")
                        } // Si Java et Git
                        else if (pipelineParams?.git) {
                        echo "On est en Git sans Angular"
                            mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                            sonarQubeAnalysis(sonarQubeEnv,"${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.4:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.scm.provider=git -Dsonar.java.binaries=target/classes")
                        } // Si Java et SVN
                        else {
                        echo "On est en SVN sans Angular"
                            mvnPerformOpts = pipelineParams?.mvnPerformOpts ?: ""
                            sonarQubeAnalysis(sonarQubeEnv,"${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:prepare-agent package license:format org.jacoco:jacoco-maven-plugin:0.8.4:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.6.0.1398:sonar")
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

            stage('Build and publish docker image with Jib') {
                when {
                    expression {pipelineParams?.isDocker && pipelineParams?.applicationName}
                }
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
                            if( env.newSnapshotVersion != env.newReleaseVersion ) {
                                sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings versions:set -DnewVersion=${env.newReleaseVersion} jib:build -Djib.to.auth.username=${ARTIFACTORY_CREDENTIALS_ID_USR} -Djib.to.auth.password=${ARTIFACTORY_CREDENTIALS_ID_PSW} -DsendCredentialsOverHttp=true -Djib.allowInsecureRegistries=true -Djib.to.tags=${env.newReleaseVersion},latest -Djib.to.image=[IP]/ans/${pipelineParams?.applicationName}"
                            }
                            else {
                                sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings versions:set -DnewVersion=${env.newReleaseVersion} jib:build -Djib.to.auth.username=${ARTIFACTORY_CREDENTIALS_ID_USR} -Djib.to.auth.password=${ARTIFACTORY_CREDENTIALS_ID_PSW} -DsendCredentialsOverHttp=true -Djib.allowInsecureRegistries=true -Djib.to.tags=${env.newReleaseVersion},latest -Djib.to.image=[IP]/ans/${pipelineParams?.applicationName}"
                            }
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
