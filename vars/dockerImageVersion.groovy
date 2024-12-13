#!/usr/bin/env groovy
import groovy.json.*

def call(def versionToScan, def response) {
    def jsonSlurper = new JsonSlurperClassic().parseText(response)
    def list = jsonSlurper.tags

    try {
        timeout(time: 30, unit: 'SECONDS') {
            versionToScan = input(
                id: 'versionToScan', message: 'Choix de la version ', parameters: [
                    [
                        $class: 'ChoiceParameterDefinition',
                        name: 'Choisissez la version à scanner',
                        choices: list,
                        description: 'Les versions sont directement récupérées du serveur Artifactory',
                    ],
                ]
            )
        }
    } catch (err) {
        echo 'Timeout - pas de choix de version du projet'
    }
    return versionToScan
}
