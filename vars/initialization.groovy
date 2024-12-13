#!/usr/bin/env groovy

def call(def svnCredentialsId, def cleanWSoption, def mailListOption, def skipTestOption, def directoryPathOption, def frontendPathOption, def mvnPerformOptsOption, def pathOfDockerfile, def projectName, def autosemver) {
    // La brance analysée correspond-elle à une norme ANS ?
    if (env.BRANCH_NAME =~ /dev(.*)/ || env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'main' || env.BRANCH_NAME =~ /release(.*)/ || env.BRANCH_NAME =~ /hotfix(.*)/ || env.BRANCH_NAME =~ /bug(.*)/ || env.BRANCH_NAME =~ /feat(.*)/ || env.BRANCH_NAME =~ /depl(.*)/ || env.BRANCH_NAME =~ /PR-([0-9+])/ ) {
        echo 'La branche analysée possède un nom conventionnel'
    } else {
        error "La branche analysée possède un nom non conventionnel. Merci de renommer ${env.BRANCH_NAME} avec un nom adapté comme indiqué dans le gitflow. La branche doit commencer en minuscules par bug, feat, hotfix, release, dev, master ou main."
    }

    // TODO : modifier env values pour Changing Credentials
    if (svnCredentialsId) {
        if (svnCredentialsId == 'TRA') {
            env.svnCredentialsId = "${env.SVN_CREDENTIALS_TRA}"
            echo "Before Maven release, svnCredentialsId is changed from ${env.SVN_CREDENTIALS_TMA} to ${svnCredentialsId}"
        }
        else {
            env.svnCredentialsId = "${svnCredentialsId}"
            echo "Before Maven release, svnCredentialsId is changed from ${env.SVN_CREDENTIALS_TMA} to ${svnCredentialsId}"
        }
    }

    // TODO : modifier env values pour On vérifie si on a une liste de diffusion en paramètre sinon, on envoie un mail au développeur qui a commit en dernier
    if (mailListOption) {
        if (mailListOption == 'TRA') {
            env.mailList = "${env.MAIL_TRA}"
            echo "Before Maven release, mailList is changed from ${env.MAIL_TMA} to ${env.mailList}"
        }
        else {
            mailList = "${mailListOption}"
            echo "Before Maven release, mailList is changed from ${env.MAIL_TMA} to ${env.mailList} but now ${mailList} which is ${mailListOption}"
        }
    } else {
        // On override le mail pour envoyer les notifications au dernier commiter (utiles pour les acteurs hors TMA)
        lastCommiterMail = sh(script: "git log -1 --pretty=format:'%ae'", returnStdout: true).trim()
        regexMail =  /[a-zA-Z0-9.'_%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,4}/
        // On vérifie la regex de lastCommiterMail et on applique lastCommiterMail si cela a le format d'un mail
        if ("${lastCommiterMail}" ==~ regexMail ) {
            env.mailList = "${lastCommiterMail}"
        }
        echo "Le mail du dernier commiter est lastCommiterMail: ${lastCommiterMail} et env.mailList = ${env.mailList}"
    }

    // Si le dernier commit est lié au maven release plugin, pas besoin de rebuild l'application sauf pour l'autoupdate
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
            // Quel est le dernier log de commit pour Git ?
            lastCommit = sh(
                script: 'git log -1',
                returnStdout: true
            ).trim()
            echo "Le dernier commit lastCommit est : ${lastCommit}"

            // If maven plugin, we do nothing except if it's a cron task for autoupdate
            if ( lastCommit.contains('maven-release-plugin') && !currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause')) {
                echo 'WARNING : Attention, le dernier log contient [maven-release-plugin] et donc on ne relance pas de job pour éviter de relancer un job inutilement.'
                currentBuild.getRawBuild().getExecutor().interrupt(Result.SUCCESS)
                sleep(3)   // Interrupt is not blocking and does not take effect immediately.
            } else if ( lastCommit.contains('Maj release note') && !currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause')) {
                echo 'WARNING : Attention, le dernier log contient [Maj release note] et donc on ne relance pas de job pour éviter de relancer un job inutilement.'
                currentBuild.getRawBuild().getExecutor().interrupt(Result.SUCCESS)
                sleep(3)   // Interrupt is not blocking and does not take effect immediately.
            } else {
                echo 'Le dernier commit est un commit de développeur. Le reste des actions peut continuer.'
            }
        }
    }

    // Option pour nettoyer le workspace et refaire le checkout des sources (positioner à "true" pour l'activer)
    if (cleanWSoption) {
        echo 'Clean Workspace : OK'
        cleanWs()
        checkout scm
    }

    // SkipTests
    if (skipTestOption) {
        env.skipTest = true
    } else {
        env.skipTest = false
    }
    echo "Skiptest is ${env.skipTest}"

    // Nouveau chemin si le pom n'est pas à la racine.
    if (directoryPathOption) {
        env.directoryPath = directoryPathOption
        dir(env.directoryPath) {
            // Récupération de nom de l'artifact et de la version depuis le pom à l'aide du Pipeline Utility Steps plugin
            env.pomVersion = readMavenPom().getVersion()
            env.pomArtifactId = readMavenPom().getArtifactId()
        }
    }
    else {
        env.directoryPath = ''
        // Sinon, on renseigne la valeur du pom à la racine
        env.pomVersion = readMavenPom().getVersion()
        env.pomArtifactId = readMavenPom().getArtifactId()
    }
    env.newSnapshotVersion = env.pomVersion
    env.newReleaseVersion = env.pomVersion
    echo "Before pomVersion est = ${env.pomVersion} et pomArtifactId = ${env.pomArtifactId} et directoryPath ${env.directoryPath} et newSnapshotVersion = ${env.newSnapshotVersion} et newReleaseVersion = ${env.newReleaseVersion}"

    // Déclaration du tag name pour changelog
    env.versionTagName = env.newSnapshotVersion.replace('-SNAPSHOT', '').replace('\n', '')

    // Prérequis : il faut que le job ait "Fetch tags"
    if (autosemver) {
        // Get actual hash
        gitActualHash = sh(
            script: 'git rev-parse HEAD',
            returnStdout: true
        ).trim()
        // Get latest hash of tag
        gitLatestTagHash = sh(
            script: 'git rev-list -n 1 $(git describe --tags $(git rev-list --tags --max-count=1))',
            returnStdout: true
        ).trim()
        // Get log list from latest tag to latest hash
        gitLogBetweenHash = sh(
            script: "git log  --no-merges --oneline --pretty=format:%s ${gitLatestTagHash}..${gitActualHash}",
            returnStdout: true
        ).trim()
        // En fonction du log, quel digit doit-on incrémenter ?
        if (gitLogBetweenHash.contains('BREAKING CHANGE:')) {
            scope = 'major'
        } else if (gitLogBetweenHash.contains('feat:')) {
            scope = 'minor'
        } else {
            scope = 'patch'
        }
        // Incrément de la version release et la prochaine snapshot
        def (major, minor, patch) = versionTagName.tokenize('.').collect { it.toInteger() }
        switch (scope) {
            case 'major':
                env.nextVersion = "${major + 1}.0.0"
                env.nextVersionSnapshot = "${major + 1}.0.1-SNAPSHOT"
                break
            case 'minor':
                env.nextVersion = "${major}.${minor + 1}.0"
                env.nextVersionSnapshot = "${major}.${minor + 1}.1-SNAPSHOT"
                break
            case 'patch':
                env.nextVersion = "${major}.${minor}.${patch + 1}"
                env.nextVersionSnapshot = "${major}.${minor}.${patch + 2}-SNAPSHOT"
                break
        }
        echo('On est dans un scope ' + scope + ' donc la nextVersion = ' + nextVersion + ' et nextVersionSnapshot = ' + nextVersionSnapshot)
    }

    // Changement du frontendpath au besoin
    if (frontendPathOption) {
        env.frontendPath = frontendPathOption
    } else {
        env.frontendPath = 'src/main/frontend'
    }
    echo "Path for front end is changed to ${env.frontendPath}"

    // Ajout des mvnOptions en paramètres global
    if (mvnPerformOptsOption) {
        env.mvnPerformOpts = mvnPerformOptsOption
    } else {
        env.mvnPerformOpts = ''
    }

    if (projectName) {
        env.projectName = projectName
    } else {
        env.projectName = ''
    }
}
