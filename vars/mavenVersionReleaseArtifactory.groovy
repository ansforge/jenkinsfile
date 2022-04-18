#!/usr/bin/env groovy

def call(def developmentVersion, def releaseVersion, def svnCredentialsId, def targetRepo, def mvnPerformOpts, def git, def autoUpdateBranchName) {
	withCredentials([usernamePassword(credentialsId: "${svnCredentialsId}", passwordVariable: 'PASSWORD_VAR', usernameVariable: 'USERNAME_VAR')]) {
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
		]) 
		{

			echo "Play Maven Clean"
			sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:clean"
			
			if (git == "true" && autoUpdateBranchName == "auto-update") {
					echo "Play Maven release Git autoupdate"
					sh "mvn -Dusername=${USERNAME_VAR} -Dpassword=${PASSWORD_VAR} -s $ASIPProfilesSettings -gs $ASIPGlobalSettings -DupdateBranchVersions=true -DupdateWorkingCopyVersions=false --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:prepare -Dproject.scm.developerConnection=\"scm:git:${env.GIT_URL}\" -DdevelopperConnectionJenkinsfile=\"scm:git:${env.GIT_URL}\" -DcheckModificationExcludeList=ASIPProfilesSettings.xml,ASIPGlobalSettings.xml,pom.xml.versionsBackup,dependency-check-report.xml,owasp.log,**/velocity.log,**/sgc-contrats_tests.log,**/linux-x64-48_binding.node,**/node-v6.11.0-linux-x64.tar.gz,**/node-v12.14.1-linux-x64.tar.gz,**/node-v16.13.0-linux-x64.tar.gz,**/package-lock.json,**/package.json,**/sgc-habilitations_tests.log,**/enreg-gateway.log,**/linux-x64-**_binding.node**,**/node-v**-linux-x64.tar.gz"
				} else if (git == "true") {
					echo "Play Maven release Git"
					sh "mvn -Dusername=${USERNAME_VAR} -Dpassword=${PASSWORD_VAR} -s $ASIPProfilesSettings -gs $ASIPGlobalSettings --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:prepare -Dproject.scm.developerConnection=\"scm:git:${env.GIT_URL}\" -DdevelopperConnectionJenkinsfile=\"scm:git:${env.GIT_URL}\" -DnewVersion=${releaseVersion} -DreleaseVersion=${releaseVersion} -DdevelopmentVersion=${developmentVersion} -DcheckModificationExcludeList=ASIPProfilesSettings.xml,ASIPGlobalSettings.xml,pom.xml.versionsBackup,dependency-check-report.xml,owasp.log,**/velocity.log,**/sgc-contrats_tests.log,**/linux-x64-48_binding.node,**/node-v6.11.0-linux-x64.tar.gz,**/node-v12.14.1-linux-x64.tar.gz,**/node-v16.13.0-linux-x64.tar.gz,**/package-lock.json,**/package.json,**/sgc-habilitations_tests.log,**/enreg-gateway.log,**/linux-x64-**_binding.node**,**/node-v**-linux-x64.tar.gz"
				} else {
					echo "Play Maven release SVN"
					sh "mvn -Dusername=${USERNAME_VAR} -Dpassword=${PASSWORD_VAR} -s $ASIPProfilesSettings -gs $ASIPGlobalSettings --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:prepare -Dproject.scm.developerConnection=\"scm:svn:${env.SVN_URL}\" -DdevelopperConnectionJenkinsfile=\"scm:svn:${env.SVN_URL}\" -DnewVersion=${releaseVersion} -DdevelopmentVersion=${developmentVersion} -DcheckModificationExcludeList=ASIPProfilesSettings.xml,ASIPGlobalSettings.xml,pom.xml.versionsBackup,dependency-check-report.xml,owasp.log,**/velocity.log,**/sgc-contrats_tests.log,**/linux-x64-48_binding.node,**/node-v6.11.0-linux-x64.tar.gz,**/node-v12.14.1-linux-x64.tar.gz,**/node-v16.13.0-linux-x64.tar.gz,**/package-lock.json,**/package.json,**/sgc-habilitations_tests.log,**/enreg-gateway.log,**/linux-x64-**_binding.node**,**/node-v**-linux-x64.tar.gz"
			}

			echo "Play Maven Perform"
			sh "mvn -s $ASIPProfilesSettings -gs $ASIPGlobalSettings --batch-mode org.apache.maven.plugins:maven-release-plugin:3.0.0-M4:perform -Darguments=\"-Dmaven.test.skip=true $mvnPerformOpts -DusernameRepo=${ARTIFACTORY_CREDENTIALS_ID_USR} -DpasswordRepo=${ARTIFACTORY_CREDENTIALS_ID_PSW} -Dmaven.source.skip=true -DdistributionRepositoryJenkinsfile=http://[IP]/artifactory/${targetRepo}\""
		}
	}
}
