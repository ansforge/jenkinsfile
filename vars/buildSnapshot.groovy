#!/usr/bin/env groovy

def call(def artifactoryServerId, def targetRepo, def featureBranchSnapshot) {
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
        // Init Maven Deployer
        rtMavenDeployer(
            id: 'MAVEN_DEPLOYER',
            serverId: "${artifactoryServerId}",
            releaseRepo: "${targetRepo}",
            snapshotRepo: "${targetRepo}"
        )

        //Init Maven Resolver
        rtMavenResolver(
            id: 'MAVEN_RESOLVER',
            serverId: "${artifactoryServerId}",
        )

        if (featureBranchSnapshot) {
            // Maven create release
            rtMavenRun(
                    pom: 'pom.xml',
                    goals: 'clean install -s $ASIPProfilesSettings -gs $ASIPGlobalSettings -DskipTests=true',
                    deployerId: 'MAVEN_DEPLOYER',
                    resolverId: 'MAVEN_RESOLVER'
                )

            // Publish Build Info
            rtPublishBuildInfo(
                    serverId: "${artifactoryServerId}"
                )
        }
        else {
            // Définir si on est sur une branche stable : dev, develop ou master
            if (env.BRANCH_NAME =~ /dev(.*)/ || env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'main' || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/ ) {
                echo 'Branche stable : main, master, dev, release, bugfix ou hotfix'

                // Maven create release
                rtMavenRun(
                    pom: 'pom.xml',
                    goals: 'clean install -s $ASIPProfilesSettings -gs $ASIPGlobalSettings -DskipTests=true',
                    deployerId: 'MAVEN_DEPLOYER',
                    resolverId: 'MAVEN_RESOLVER'
                )

                // Publish Build Info
                rtPublishBuildInfo(
                    serverId: "${artifactoryServerId}"
                )
            }
            else {
                echo "On ne pousse pas les snapshot dans Artifactory car ce n'est pas une branche stable : main, master, dev, release, bugfix ou hotfix"
            }
        }
    }
}
