#!/usr/bin/env groovy
import groovy.json.*

def call(def svnCredentialsId, def urlGitApplication, def versionTagName, def branchName) {
    withCredentials([usernamePassword(credentialsId: "${env.svnCredentialsId}", passwordVariable: 'PASSWORD_VAR', usernameVariable: 'USERNAME_VAR')]) {
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
        )]) {
            env.urlGitApplicationChangelog = urlGitApplication
            env.branchNameChangelog = branchName
            env.versionTagNameChangelog = versionTagName

            sh '''
                chmod +x changelog.sh
                echo "L'url du projet est ${urlGitApplicationChangelog} $urlGitApplicationChangelog et ${branchNameChangelog} = $branchNameChangelog et versionTagNameChangelog = $versionTagNameChangelog = ${versionTagNameChangelog} "
                ./changelog.sh ${branchNameChangelog}
                git add CHANGELOG.md
            '''
            sh "git commit -am 'Maj release note version ${versionTagNameChangelog}'"
            sh '''
                echo "L'url du projet est ${urlGitApplicationChangelog} $urlGitApplicationChangelog"
                git push "https://${USERNAME_VAR}:${PASSWORD_VAR}@${urlGitApplicationChangelog}"
            '''
        }
    }
}
