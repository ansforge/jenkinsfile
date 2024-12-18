#!/usr/bin/env groovy

def call(def sonarQubeEnv, def mvnOpts="org.jacoco:jacoco-maven-plugin:prepare-agent package org.jacoco:jacoco-maven-plugin:report org.sonarsource.scanner.maven:sonar-maven-plugin:sonar") {
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
            // TODO Verify afin de tester également les tests d'intégration 05092024 mvn clean verify
            withEnv(["mvnOpts=$mvnOpts"]) {
                sh('mvn clean dependency:copy-dependencies -U -s $ASIPProfilesSettings -gs $ASIPGlobalSettings ${mvnOpts}')
            }
        }
        }
}
