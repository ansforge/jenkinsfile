#!/usr/bin/env groovy

def call(def propVersion) {
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
        sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings versions:set -DnewVersion=${propVersion} -DprocessAllModules"
        sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings versions:commit -DprocessAllModules"
    }
}
