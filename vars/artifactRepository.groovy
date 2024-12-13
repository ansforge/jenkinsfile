#!/usr/bin/env groovy
package fr.ans

def call(def propVersion) {
    targetRepo = 'error'
    regexReleaseThreeDigits = /\d+\.\d+\.\d+/
    regexSnapshotThreeDigits = regexReleaseThreeDigits + '-SNAPSHOT.*'

    if ("${propVersion}" ==~ regexReleaseThreeDigits) {
        targetRepo = 'asip-releases'
    }
    if ("${propVersion}" ==~ regexSnapshotThreeDigits) {
        targetRepo = 'asip-snapshots'
    }
    if (targetRepo == 'error') {
        failureMessage = "Problème lors de l'écriture de la version : cela doit respecter le semantic versioning X.Y.Z !"
        currentBuild.result = 'FAILURE'
        error('Version erronée : cela doit respecter le semantic versioning X.Y.Z !')
    }
    return targetRepo
}

