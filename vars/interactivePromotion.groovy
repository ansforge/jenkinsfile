#!/usr/bin/env groovy

def call(def artifactoryServerId, def targetRepo)  {
	rtAddInteractivePromotion (
		// Paramètres obligatoires
		serverId: "${artifactoryServerId}",
		// Paramètres optionnels
		targetRepo: targetRepo,
		displayName: 'Processus de promotion ASIP',
		comment: "Promotion du build ASIP ${BUILD_TAG}",
		sourceRepo: 'asip-snapshots',
		status: 'Released',
		includeDependencies: true,
		failFast: true,
		copy: true
	)
}
