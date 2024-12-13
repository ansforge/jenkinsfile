#!/usr/bin/env groovy

def call(def sonarQubeEnv, def mvnOpts="org.jacoco:jacoco-maven-plugin:prepare-agent org.jacoco:jacoco-maven-plugin:report org.sonarsource.scanner.maven:sonar-maven-plugin:sonar") {
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
            echo "Beginning of Sonarqube"
            sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings $mvnOpts"
        }
        }
}
