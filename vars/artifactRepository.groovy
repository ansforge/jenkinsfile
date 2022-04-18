#!/usr/bin/env groovy
package fr.ans

def call(def propVersion) {
	targetRepo = "error"
	regexRelease = /\d+\.\d+\.\d+\.\d+/
	regexReleaseThreeDigits = /\d+\.\d+\.\d+/
	regexReleaseCandidate = regexRelease + '-RC.*'
	regexReleaseCandidateThreeDigits = regexReleaseThreeDigits + '-RC.*'
	regexSnapshot = regexRelease + '-SNAPSHOT.*'
	regexSnapshotThreeDigits = regexReleaseThreeDigits + '-SNAPSHOT.*'

	if ("${propVersion}" ==~ regexReleaseCandidate){
		targetRepo = 'asip-releases-candidate'
	}
	if ("${propVersion}" ==~ regexReleaseCandidateThreeDigits){
		targetRepo = 'asip-releases-candidate'
	}
	if ("${propVersion}" ==~ regexRelease){
		targetRepo = 'asip-releases'
	}
	if ("${propVersion}" ==~ regexReleaseThreeDigits){
		targetRepo = 'asip-releases'
	}
	if ("${propVersion}" ==~ regexSnapshot){
		targetRepo = 'asip-snapshots'
	}
	if ("${propVersion}" ==~ regexSnapshotThreeDigits){
		targetRepo = 'asip-snapshots'
	}
	if (targetRepo == 'error') {
		failureMessage = "Problème lors de l'écriture de la version : cela doit respecter le semantic versioning X.Y.Z.A !"
		currentBuild.result = 'ABORTED'
		error('Version erronée … Veuillez choisir SNAPSHOT, RELEASE ou RC')
	}
	
	return targetRepo
}	
					