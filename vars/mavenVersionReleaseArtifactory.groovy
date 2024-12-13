#!/usr/bin/env groovy

def call(def developmentVersion, def releaseVersion, def svnCredentialsId, def targetRepo, def mvnPerformOpts, def customParameter, def directoryPath, def isDockerDaemon) {
    withCredentials([usernamePassword(credentialsId: env.svnCredentialsId, passwordVariable: 'PASSWORD_VAR', usernameVariable: 'USERNAME_VAR')]) {
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
        ])
        {
            // Path à ajouter dans la liste des exceptions
            directoryPath = directoryPath + '/'
            echo 'Play Maven Clean'
            sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:clean"
            if (customParameter == 'auto-update') {
                echo 'Play Maven release autoupdate'
                sh "mvn -Dusername=${USERNAME_VAR} -Dpassword=${PASSWORD_VAR} -s $ASIPProfilesSettings -gs $ASIPGlobalSettings -DupdateBranchVersions=true -DupdateWorkingCopyVersions=false --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:prepare -Dproject.scm.developerConnection=\"scm:git:${env.GIT_URL}\" -DdevelopperConnectionJenkinsfile=\"scm:git:${env.GIT_URL}\" -DcheckModificationExcludeList=x64.tar.gz"
            } else {
                echo 'Play Maven release'
                echo "username : ${USERNAME_VAR} "
                sh "mvn -Dusername=${USERNAME_VAR} -Dpassword=${PASSWORD_VAR} -s $ASIPProfilesSettings -gs $ASIPGlobalSettings --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:prepare -Dproject.scm.developerConnection=\"scm:git:${env.GIT_URL}\" -DdevelopperConnectionJenkinsfile=\"scm:git:${env.GIT_URL}\" -DnewVersion=${releaseVersion} -DreleaseVersion=${releaseVersion} -DdevelopmentVersion=${developmentVersion} -DcheckModificationExcludeList=ok.gz"

            // Si l'option Docker est activée, on ne push pas les .jar sur l'artifactory.
            // L'image docker va être push et est suffisante.
            if (!isDockerDaemon) {
                echo 'Play Maven Perform'
                if (customParameter == 'dontSkipMavenSourceJar') {
                    sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:perform -Darguments=\"-Dmaven.test.skip=true $mvnPerformOpts -DusernameRepo=${ARTIFACTORY_CREDENTIALS_ID_USR} -DpasswordRepo=${ARTIFACTORY_CREDENTIALS_ID_PSW} -DdistributionRepositoryJenkinsfile=https://xxx/artifactory/${targetRepo}\""
                } else {
                    sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:perform -Darguments=\"-Dmaven.test.skip=true $mvnPerformOpts -DusernameRepo=${ARTIFACTORY_CREDENTIALS_ID_USR} -DpasswordRepo=${ARTIFACTORY_CREDENTIALS_ID_PSW} -Dmaven.source.skip=true -DdistributionRepositoryJenkinsfile=https://xxx/artifactory/${targetRepo}\""
                }
            }
        }
    }
}
