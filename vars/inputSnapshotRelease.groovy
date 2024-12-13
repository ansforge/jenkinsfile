#!/usr/bin/env groovy

def call() {
    try {
        // env.INPUT_TIMEOUT
        timeout(time: 30, unit: 'SECONDS') {
            def myParam = input message: 'Veuillez renseigner le numéro de version de la prochaine snapshot ainsi que confirmer le numéro de cette release', ok: 'Release', id: 'release_id',
                    parameters: [string(defaultValue: "${pomVersion}", description: 'Prochaine version Snapshot', name: 'newSnapshotVersion'),
                                 string(defaultValue: "${pomVersion}", description: 'Version de Release', name: 'newReleaseVersion')]
            env.newSnapshotVersion = myParam.newSnapshotVersion
            env.newReleaseVersion = myParam.newReleaseVersion
        }
    } catch (err) {
        // Récupération de nom de l'artifact et de la version depuis le pom à l'aide du Pipeline Utility Steps plugin
        env.newSnapshotVersion = "${pomVersion}"
        env.newReleaseVersion = "${pomVersion}"
        echo "Timeout - pas de release - pomVersion = ${pomVersion}"
    }
}
