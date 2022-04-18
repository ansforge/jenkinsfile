#!/usr/bin/env groovy

def call(def artifactoryServerId, def targetRepo) {
	rtMavenDeployer (
		id: "MAVEN_DEPLOYER",
		serverId: "${artifactoryServerId}",
		releaseRepo: "${targetRepo}",
		snapshotRepo: "asip-snapshots"
	)
}