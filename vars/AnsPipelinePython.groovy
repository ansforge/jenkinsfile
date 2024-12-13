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
            // Credentials Git
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
            stage('Initialize') {
                steps {
                    script {
                        // On override le mail si besoin (utiles pour les acteurs hors TMA)
                        lastCommiterMail = sh(script: "git log -1 --pretty=format:'%ae'", returnStdout: true).trim()
                        echo "lastCommiterMail: ${lastCommiterMail}"
                        // On a la regex d'un mail
                        regexMail =  /[a-zA-Z0-9.'_%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,4}/
                        // On vérifie la regex de lastCommiterMail et on applique lastCommiterMail si cela a le format d'un mail
                        if (pipelineParams?.mailList) {
                            if (pipelineParams?.mailList == 'TRA') {
                                mailList = "${env.MAIL_TRA}"
                                echo "Before Maven release, mailList is changed from ${env.MAIL_TMA} to ${mailList}"
                            }
                            else {
                                mailList = pipelineParams?.mailList
                                echo "Before Maven release, mailList is changed from ${env.MAIL_TMA} to ${mailList}"
                            }
                        } else {
                            if ("${lastCommiterMail}" ==~ regexMail ) {
                                mailList = "${lastCommiterMail}"
                            }
                        }

                        // La brance analysée correspond-elle à une norme ANS ?
                        if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'develop' || env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'main' || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/ || env.BRANCH_NAME =~ /bug(.*)/ || env.BRANCH_NAME =~ /feat(.*)/ ) {
                            echo 'La branche analysée possède un nom conventionnel'
                        }
                        else {
                            error 'La branche analysée possède un nom non conventionnel. Merci de renommer {env.BRANCH_NAME} avec un nom adapté comme indiqué dans le gitflow. La branche doit commencer en minuscules par bug, feat, hotfix, release, dev, master ou main.'
                        }
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
                        if (pipelineParams?.pythonVersion && pipelineParams?.applicationName) {
                            if (pipelineParams?.pythonTests) {
                                sh '''
                                pip install --user -r requirements.txt
                                python3 -m coverage run -m unittest discover test
                                python3 -m coverage xml
                            '''
                            }
                            def scannerHome = tool 'sonar-scanner'
                            withSonarQubeEnv(sonarQubeEnv) {
                                sh """
                            version=\$(perl -ne 'print \$1 if /"version".*"(.*)"/' ./metadata.json)
                            ${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${pipelineParams?.applicationName} -Dsonar.python.coverage.reportPaths=coverage.xml  -Dsonar.projectname=${pipelineParams?.applicationName} -Dsonar.projectVersion=\$version -Dsonar.branch.name=${env.BRANCH_NAME} -Dsonar.source=apps -Dsonar.python.version=${pipelineParams?.pythonVersion} -Dsonar.exclusions=tools/*/**,src/main/frontend/dist/**,src/main/frontend/e2e/**,src/main/frontend/node_modules/**,src/main/frontend/node/**,**/*.spec.ts,**/*.conf.js,**/*.TestDataService.ts,static/**,files/static/**
                        """
                            }
                    } else {
                            echo 'Warning ! Mettez dans les pipelineParams un applicationName et pythonVersion'
                        }
                    }
                }
            }

            stage('Generation de l\'archive de livraison') {
                steps {
                    script {
                        if (pipelineParams?.build) {
                                sh('sh build.sh')
                        } else {
                            echo 'Warning ! Mettez dans les pipelinesParams un build et un build.sh dans le projet pour savoir comment builder'
                        }
                    }
                }
            }

            stage('Push to artifactory') {
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
                            if (artifactoryPrimaryPath == 'asip-releases') {
                                if ( env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'main' || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/ || env.BRANCH_NAME =~ /bug(.*)/) {
                                    echo 'Branche stable'
                            } else {
                                    echo 'Pas une branche stable : main, master, release, bugfix ou hotfix'
                                    error "La branche ${env.BRANCH_NAME} ne doit pas être utilisée pour faire des releases. La branche doit etre master ou main ou release ou dans des cas particuliers : bug, hotfix."
                                }
                            }
                            def server = Artifactory.server "${artifactoryServerId}"
                            def version = sh(script: ''' perl -ne 'print $1 if /"version".*"(.*)"/' ./metadata.json ''', returnStdout:true)
                            echo "Artifactory target = ${artifactoryPrimaryPath}/${artifactoryPath}/${version}/ "
                            def uploadSpec = """{
                            "files": [{
                            "pattern": "pkg/*.tar.gz",
                            "target": "${artifactoryPrimaryPath}/${artifactoryPath}/${version}/"
                            }]
                        }"""
                            server.upload(uploadSpec)
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
