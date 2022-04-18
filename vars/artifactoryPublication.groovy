#!/usr/bin/env groovy

def call() {
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
		rtMavenRun (
			pom: 'pom.xml',
			goals: 'verify -s $ASIPProfilesSettings -gs $ASIPGlobalSettings -DskipTests=true ',
			deployerId: "MAVEN_DEPLOYER"
		)
	}
}