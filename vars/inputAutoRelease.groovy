#!/usr/bin/env groovy

def call() {
    try {
        timeout(time: 30, unit: 'SECONDS') {
            def myParam = input message: 'Voulez-vous faire une release ?', ok: 'Créer une release', id: 'release_id'
            // Mise à jour des valeurs avec les valeurs autogénérées
            env.newSnapshotVersion = env.nextVersionSnapshot
            env.newReleaseVersion = env.nextVersion
        }
    } catch (err) {
        // Pas de release
        echo "Timeout - pas de release - pomVersion = ${pomVersion}"
    }
}
