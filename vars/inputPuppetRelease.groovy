#!/usr/bin/env groovy

def call() {
    try {
        timeout(time: 30, unit: 'SECONDS') {
            env.artifactoryPrimaryPath = input message: 'Par défaut puppet-snapshots, vous pouvez choisir un autre repository', ok: 'Lancer le build !',
                parameters: [choice(name: 'artifactoryPrimaryPath', choices: ['puppet-snapshots', 'puppet-releases'], description: 'Path Artifactory')]
        }
    } catch (err) {
        echo 'Timeout - pas de déploiement sur puppet-releases'
    }
}
