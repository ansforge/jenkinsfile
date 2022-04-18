import groovy.json.*

def call(Map pipelineParams) {
    pipeline {
        agent {
            label 'java-slaves'
        }
		environment {
            // Version utilisée de SonarQube 
            sonarQubeEnv = "${env.SONARQUBE_VERSION}"
			// Credentials SVN
            svnCredentialsId = "${env.SVN_CREDENTIALS_TMA}"
            // Destinataires du mail de notification
            mailList = "${env.MAIL_TMA}"
        }
        stages {
            stage ('Choose deploy version') {
                steps {
                    script {
						// Version to deploy
						versionToDeploy = ""

						// select version from imported parameters
						if (pipelineParams?.versionToDeploy) {
							versionToDeploy = params?.versionToDeploy
						} else if (pipelineParams?.applicationKey && pipelineParams?.applicationName && pipelineParams?.targetRepo) {
							versionToDeploy = deployVersion(versionToDeploy, pipelineParams?.applicationKey, pipelineParams?.applicationName, pipelineParams?.targetRepo) 
						}

						echo ("Selected versionToDeploy is " + versionToDeploy)

						if (versionToDeploy == "") {
							// End the build because we want only to autoupdate 
                            currentBuild.getRawBuild().getExecutor().interrupt(Result.SUCCESS)
                            sleep(3)   // Interrupt is not blocking and does not take effect immediately.
						}
					}
				}
			}
			// Find version to change in application.yml 
            stage('Change version in application yaml') {
                steps {
                    script {
						if (pipelineParams?.labelParametreVersionApplicationYaml && pipelineParams.pathToApplicationYaml) {
							CMD = sh (
							script: "sed 's/.*${pipelineParams?.labelParametreVersionApplicationYaml}.*/${pipelineParams?.labelParametreVersionApplicationYaml}: \"${versionToDeploy}\"/' ${env.WORKSPACE}/${pipelineParams.pathToApplicationYaml} > changed.txt && mv changed.txt ${env.WORKSPACE}/${pipelineParams.pathToApplicationYaml}",
							returnStdout: true
							).trim()
							echo "CMD: ${CMD}"
						}
	                }
				}
			}

			// Commit to SVN
            stage('Commit application yaml in SVN') {
                steps {
                    script {					
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
								echo "Commit the file with the modified version to svn"
								sh "svn status"
								sh "svn commit -m 'Update ${pipelineParams?.applicationName} version to ${versionToDeploy}' --username ${USERNAME_VAR} --password ${PASSWORD_VAR} --no-auth-cache --non-interactive"
							}
						}
					}
				}
			}
		}
	}
}
