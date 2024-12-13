#!/usr/bin/env groovy

def call(def sonarQubeEnv) {
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
            echo 'Beginning of Quality Gate'
        /*    timeout(time: 3, unit: 'MINUTES') {
                // TODO : réactiver l'étape quand on l'ouverture de flux permettra à Jenkins d'interroger Sonar
                def qualitygate = waitForQualityGate()
                if (qualitygate.status != "OK") {
                    error "Le quality gate est en erreur : ${qualitygate.status}. Il faut corriger les problèmes et relancer le build."
                } else {
                    echo "Quality gate ok"
                }
            }*/
        }
}
}
