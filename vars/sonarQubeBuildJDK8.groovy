#!/usr/bin/env groovy

def call(def sonarQubeEnv, def mvnOpts="package") {
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
        withSonarQubeEnv("${sonarQubeEnv}") {
            echo "Beginning build in JDK8"
            sh "mvn clean -s $ASIPProfilesSettings -gs $ASIPGlobalSettings $mvnOpts"
        }
        }
}
