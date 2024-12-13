#!/usr/bin/groovy

def call(Map pipelineParams) {
    pipeline {
        agent {
            label "${pipelineParams?.runner ? pipelineParams.runner : 'nomadBookwormJDK17' }"
        }
        triggers {
            cron(env.BRANCH_NAME == 'feat/auto-update' ? '0 8 * * *' : '')
        }
        environment {
            // Version utilisée de SonarQube 
            sonarQubeEnv = "${env.SONARQUBE_VERSION}"
			// Credentials SVN
            //svnCredentialsId = "${env.SVN_CREDENTIALS_TMA}"
			svnCredentialsId = "${pipelineParams?.svnCredentialsId ? pipelineParams.svnCredentialsId : 'jenkins_ans'}"
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
            maven "${pipelineParams?.maven ? pipelineParams.maven : "${env.MVN_39}" }"
            // jdk "${pipelineParams?.jdk ? pipelineParams.jdk : "${env.JDK_17}" }"
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
                        echo "NewSnapshotVersion ${env.newSnapshotVersion} - NewReleaseVersion ${env.newReleaseVersion} and mailList = ${mailList} or ${env.mailList}"
                        echo "Credentials : ${svnCredentialsId}"
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
                        owaspCheckDependencyNomad()
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
                                sonarQubeAnalysis(sonarQubeEnv, "${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:0.8.8:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.8:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.scm.provider=git -Dsonar.sources=${frontendPath} -Dsonar.exclusions=${frontendPath}/coverage/**,${frontendPath}/dist/**,${frontendPath}/e2e/**,${frontendPath}/node_modules/**,${frontendPath}/node/**,**/*.spec.ts,**/*.conf.js,**/*.TestDataService.ts,frontend/src/assets/** -Dsonar.nodejs.executable=node-v18.15.0-linux-x64/bin/node -Dsonar.tests=${frontendPath} -Dsonar.test.inclusions=**/*.spec.ts -Dsonar.ts.tslint.configPath=${frontendPath}/tslint.json -Dsonar.ts.tslint.path=target/sonar/sonarts-bundle/node_modules/.bin/tslint -Dsonar.typescript.lcov.reportPaths=target/coverage/lcov.info  -Dsonar.dependencyCheck.htmlReportPath=./dependency-check-report.html")
                            } else if (pipelineParams?.isMvnVerify){
                                echo "On est en Java et on a besoin de mvn verify"
                                sonarQubeAnalysis(sonarQubeEnv,"${mvnPerformOpts} verify org.jacoco:jacoco-maven-plugin:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.8:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.scm.provider=git -Dsonar.java.binaries=target/classes -Dsonar.java.libraries=${env.HOME}/.m2/repository/org/projectlombok/lombok/**/*.jar -Dsonar.coverage.jacoco.xmlReportPaths=/target/site/jacoco/jacoco.xml  -Dsonar.dependencyCheck.htmlReportPath=./dependency-check-report.html")
                            } 
                            else {
                                echo "On est en Java"
                                sonarQubeAnalysis(sonarQubeEnv,"${mvnPerformOpts} org.jacoco:jacoco-maven-plugin:prepare-agent package org.jacoco:jacoco-maven-plugin:0.8.8:report org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.scm.provider=git -Dsonar.java.binaries=target/classes -Dsonar.coverage.jacoco.xmlReportPaths=/target/site/jacoco/jacoco.xml  -Dsonar.dependencyCheck.htmlReportPath=./dependency-check-report.html")
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
                        sh '''
                            git config --global user.email "jenkins@ok.fr"
                            git config --global user.name "Jenkins"
                        '''
                        dir(env.directoryPath){
                            if( env.BRANCH_NAME == "master" || env.BRANCH_NAME == "main" || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/ ) {       
                                    echo "credentials : ${svnCredentialsId}"							
                                    mavenVersionReleaseArtifactory("${env.newSnapshotVersion}", "${env.newReleaseVersion}", "${svnCredentialsId}", "${targetRepo}", "${mvnPerformOpts}", "", "${env.directoryPath}", pipelineParams?.isDockerDaemon)
                            }
                            else {
                                echo "Pas une branche stable : main, master, dev, release, bugfix ou hotfix"
                                error "La branche {env.BRANCH_NAME} ne doit pas être utilisée pour faire des releases. La branche doit etre master ou main ou dans des cas particuliers : bug, hotfix ou release."
                            }
                        }
                    }
                }
            }

            stage('Modifier la version avec un hash du commit') {
                when {
                    expression {pipelineParams?.isVersionHashed && targetRepo != "asip-releases"}
                }
                steps {
                    script {
                        env.trueSnapshotVersion = env.newSnapshotVersion
                        def hash = sh script:'git rev-parse HEAD', returnStdout: true
                        env.newSnapshotVersion = env.newSnapshotVersion.replace("SNAPSHOT", hash).replace("\n","")
                        env.newReleaseVersion = env.newSnapshotVersion.replace("SNAPSHOT", hash).replace("\n","")
                        echo("env.newSnapshotVersion est égal à ${env.newSnapshotVersion} alors que trueSnapshotVersion = ${env.trueSnapshotVersion}")
                    }
                }
            }          

            stage('Build and publish multiple docker image with Docker Daemon and Scan with Trivy') {
                when {
                    expression {pipelineParams?.isDockerDaemon && pipelineParams?.applicationName}
                }
                steps {
                    script {
                        def hash = sh script:'git rev-parse HEAD', returnStdout: true
                        dir(env.directoryPath){
                            for (int eachApplicationName = 0; eachApplicationName < pipelineParams?.applicationName.size(); eachApplicationName++) {
                                echo("Beginning of each loop and eachApplicationName = " + pipelineParams?.applicationName[eachApplicationName] + " index " + eachApplicationName + " size = " + pipelineParams?.applicationName.size() + " item " + pipelineParams?.applicationName[eachApplicationName] )
                                buildDockerImage("${env.newSnapshotVersion}", "${env.newReleaseVersion}", "${svnCredentialsId}", pipelineParams?.applicationName[eachApplicationName], pipelineParams?.pathOfDockerfile, pipelineParams?.projectName, pipelineParams)
                            }
                        }
                    }
                }
            }

            stage('Create changelog') {
                when {
                    expression {pipelineParams?.isChangelog && (env.BRANCH_NAME =~ "dev" || env.BRANCH_NAME =~ "develop" || env.BRANCH_NAME == "master" || env.BRANCH_NAME == "main" || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/)}
                }
                steps {
                    script {
                        dir(env.directoryPath){
                        sh '''
                            git config --global user.email "jenkins@ok.fr"
                            git config --global user.name "Jenkins"
                        '''
                            urlGitApplication = "${env.GIT_URL}".substring(8)
                            def hash = sh script:'git rev-parse HEAD', returnStdout: true
                            env.newSnapshotVersionHashed = env.newSnapshotVersion.replace("SNAPSHOT", hash).replace("\n","")
                            echo "Création du changelog et push sur ${urlGitApplication} avec env.versionTagName ${newSnapshotVersionHashed}"
                            changelog(svnCredentialsId, urlGitApplication, env.newSnapshotVersionHashed, env.BRANCH_NAME)
                        }
                    }
                }
            }  

             stage('Trigger Job deploy to DEV') {
                when {
                    expression { pipelineParams?.isTriggerJob && (env.BRANCH_NAME =~ "develop" || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/) }
                }
                steps {
                    script {
                        // WIP algo versionCible - Condition a tester lors de la prochaine release...
                        // Refacto ? Condition reprise de la fct BuildDockerImage donc duplication...
                        versionCible = env.newSnapshotVersion
                        if( env.newSnapshotVersion != env.newReleaseVersion ) {
                            versionCible = env.newReleaseVersion
                        }
                        envCibleValue = 'dev'
                        build job: pipelineParams?.pathJobToDeploy,
                        parameters: [string(name: 'DEPLOYMENT_ENVIRONMENT', value: envCibleValue),
                                    string(name: 'TAG_DOCKER_IMAGE', value: versionCible),
                                    string(name: 'DEPLOYMENT_TOKEN', value: 'developpement'),
                                    string(name: 'REGISTRY_DOCKER', value: 'release')], wait: false
                                    //TODO: Pointer sur la registry de snapshot.
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
            
            stage('Build Snapshot') {
                when {
                    expression { targetRepo == "asip-snapshots" }
                }
                steps{
                    script{
                        dir(env.directoryPath) {
                            //Si la version snapshot a été modifiée pour la création d'image docker,
                            //on reprend la version initiale.
                            if (pipelineParams?.isVersionHashed) {
                                env.newSnapshotVersion = env.trueSnapshotVersion
                            }
                            targetRepo = artifactRepository(env.newSnapshotVersion)
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
