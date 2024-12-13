#!/usr/bin/env groovy
import groovy.json.*

def call(def versionToDeploy, def applicationKey, def applicationName, def targetRepo) {
    def targetUrl = "https://xxx/artifactory/api/search/versions?g=${applicationKey}&a=${applicationName}&repos=${targetRepo}"
    def jsonSlupper = new JsonSlurperClassic().parse(URI.create(targetUrl).toURL())
    def list = jsonSlupper['results']['version'].collect().sort().reverse()
    try {
        timeout(time: 30, unit: 'SECONDS') {
            versionToDeploy = input(
                id: 'versionToDeploy', message: 'Choix de la release ', parameters: [
                    [
                        $class: 'ChoiceParameterDefinition',
                        name: 'Choisissez la version à installer',
                        choices: list,
                        description: 'Les versions sont directement récupérées du serveur Artifactory',
                    ],
                ]
            )
        }
    } catch (err) {
        echo 'Timeout - pas de choix de version du projet'
    }
    return versionToDeploy
}
