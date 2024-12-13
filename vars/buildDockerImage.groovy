#!/usr/bin/env groovy

def call(def developmentVersion, def releaseVersion, def svnCredentialsId, def applicationName, def pathOfDockerfile, def projectName, Map pipelineParams) {
    withCredentials([usernamePassword(credentialsId: env.svnCredentialsId, passwordVariable: 'PASSWORD_VAR', usernameVariable: 'USERNAME_VAR')]) {
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
            // Choix de la registry
            // TODO : mettre les CA sur tous les noeuds de toutes les PFC afin de snapshotRegistryServer = "registry-snapshots.url.gouv.fr"
            snapshotRegistryServer = "registry.url.gouv.fr"
            releaseRegistryServer = 'registry.url.gouv.fr'

            if ( developmentVersion != releaseVersion ) {
                registry = releaseRegistryServer
                version = releaseVersion
            } else {
                registry = snapshotRegistryServer
                version = developmentVersion
            }
            //Le nom de l'image docker est racourci avec app a la place de application.
            appDockerImage = applicationName.replace('application', 'app')
            projectView = projectName

            echo("La registry est ${registry} et la version à builder est ${version} pour l'application ${applicationName}")
            echo('Le projectName est ' + projectName + ' ou ' + projectView + " et l'applicationName est " + applicationName + ' pour un path : ' + pathOfDockerfile)
            echo("Le nom de l'image docker est " + appDockerImage)

            script {
                if (pathOfDockerfile) {
                    sh "docker build -t '$appDockerImage:${version}' -t '$appDockerImage:latest' ./$pathOfDockerfile"
                } else {
                    sh "docker build -t '$appDockerImage:${version}' -t '$appDockerImage:latest' ./$applicationName"
                }
                sh "docker tag '$appDockerImage:${version}' '${registry}/ans/$projectName/$appDockerImage:${version}'"
                sh "docker tag '$appDockerImage:latest' ${registry}/ans/$projectName/$appDockerImage:latest"
                sh "docker login --username=${ARTIFACTORY_CREDENTIALS_ID_USR} --password=${ARTIFACTORY_CREDENTIALS_ID_PSW} ${registry}"
                sh "docker push ${registry}/ans/$projectName/$appDockerImage:${version}"
                sh "docker push ${registry}/ans/$projectName/$appDockerImage:latest"
                env.pathOfApplication = "${registry}/ans/'${projectName}/${appDockerImage}':'${version}'"
            }

        // Scan de Trivy à la fin du build pour détecter les vulnérabilités dans la Docker image
        trivyScan("${pathOfApplication}",pipelineParams?.trivyParams,pipelineParams?.trivySeverity)
        }
    }
}
