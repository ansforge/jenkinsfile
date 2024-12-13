# ANS - Pipelines - Shared Library

## Pipeline

Jenkins est un serveur d'intégration continue (CI) open source, qui permet d'automatiser différentes parties du développement logiciel telles que le build, les tests et le déploiement. L'un des concepts clés de Jenkins est le pipeline, qui définit une suite d'étapes à exécuter dans un processus CI/CD.

Un pipeline Jenkins est écrit en utilisant le langage de pipeline de Jenkins (Pipeline DSL) et défini sous forme de code (Jenkinsfile) ce qui facilite la gestion des versions et la collaboration.

Un pipeline Jenkins typique comprend plusieurs étapes :

- Checkout: Récupération du code source.
- Build: Compilation du code.
- Test: Exécution des tests unitaires et d'intégration.
- Deploy: Déploiement de l'application.

Le pipeline dépend du langage de programmation et du framework utilisés.

| Pipeline        |Langage                          |Framework                         |
|----------------|-------------------------------|-----------------------------|
|AnsPipelineJdk8Mvn354          | Java 8 |Maven 3.5.4|
|AnsPipelineJdk11Mvn36          | Java 11 |Maven 3.6.x|
|AnsPipelineJdk11Mvn36Nomad          | Java 11 |Maven 3.6.x |
|AnsPipelineJdk17Mvn39          | Java 17 |Maven 3.9.x|
|AnsPipelineJdk17Mvn39Nomad          | Java 17 |Maven 3.9.x|
|AnsPipelineNpmDependency          | Node |Aucun|
|AnsPipelinePuppetNomad          | Puppet 6 |Aucun|
|AnsPipelinePython               | Python |Aucun|
|AnsPipelineAny  |Tout            |Aucun            |



Ensuite, chacun de ces pipelines peut être personnalisée en fonction à chaque étape.

| Paramètre        |Description                          |Exemple                         |
|----------------|-------------------------------|-----------------------------|
|anyLanguage               | permet de préciser que les tests sont analysés quel que soit le langage |anyLanguage: "true"|
|applicationName          | permet de définir le nom de l'application lors du build Docker |applicationName: ['finess-consultation-application']|
|artifactoryPath              | permet de définir le chemin du dépôt pour Artifactory |artifactoryPath:"ans/enreg"|
|autosemver          | permet le versioning automatique lors de la création d'une release |autosemver: "true"|
|cleanWSoption          |permet de nettoyer le workspace à chaque build.              |cleanWSoption : « true »            |
|directoryPath          | permet d'indiquer dans quel sous-dossier du dépôt on souhaite travailer |directoryPath: 'backend'|
|frontendPath          | permet d'indiquer dans quel sous-dossier du dépôt on souhaite travailer |frontendPath:"frontend"|
|mailList          | permet de modifier quel mail sera utilisé pour envoyer le rapport du build |mailList: "xxx@gouv.fr"|
|mvnPerformOpts          | permet d’ajouter des options lors du maven perform |mvnPerformOpts: "-Dsonar.language=php"|
|projectName              | permet de déclarer le nom du projet, si besoin pour créer l'image Docker |projectName: "iris"|
|skipTest          | permet de skip les tests sur Sonar. A n’utiliser que pour des projets Java sans tests |skipTest : « true »|
|svnCredentialsId  |permet de modifier le credentials utilsé par Jenkins            |svnCredentialsId: "TRA"            |
|dontSkipMavenSourceJar               | permet de construire et de garder les sources lors du build |dontSkipMavenSourceJar: "dontSkipMavenSourceJar"|
|isDockerDaemon              | permet d'indiquer qu'une image Docker doit être créée par le pipeline via un Dockerfile dans le projet |isDockerDaemon: "true"|
|pathOfDockerfile          | permet de préciser le chemin du Dockerfile s'il n'est pas à la racine |pathOfDockerfile: "frontend/"|
|trivyParams          | permet d'envoyer des paramètres à Trivy |trivyParams: "--ignore-unfixed"|
|trivySeverity               | permet d'indiquer les criticités à analyser sur l'image analysée par Trivy |trivySeverity: "HIGH,CRITICAL,MEDIUM,LOW,UNKNOWN"|
|isAngular              | permet d'indiquer que le projet est en Angular |isAngular:"true"|
|jobToDeploy          | permet d'indiquer un job Jenkins à lancer > job Puppet | pathJobToDeploy: "ANS/Annuaire/deploiement/develop"|
|isTriggerJob          | permet de conditionner le job à trigger pour les jobs de déploiement | isTriggerJob:"true"|
|pathJobToDeploy          | permet d'indiquer un job Jenkins à lancer > job de deploiement type Nomad | pathJobToDeploy: "ANS/Annuaire/deploiement/develop"|
|isChangelog          | permet de créer un changelog à partir d'un script shell et de le pousser dans l'application | isChangelog:"true"|
|featureBranchSnapshot               | permet de créer les snapshots des branches feature/ et de les pousser dans Artifactory |featureBranchSnapshot:"true"|
|artifactoryPrimaryPath              | permet de spécifier le début du chemin sur Artifactory directement pour les jobs Puppet |artifactoryPrimaryPath:"ans"|
|pythonVersion          | permet de déclarer la version de Python |pythonVersion:"3.7"|
|pythonTests               | permet de lancer les tests sur Python |pythonTests:"true"|
|versionToDeploy               | permet de définir la version à déployer dans le application.yml pour le pipeline envp6 |versionToDeploy:"1.2.3"|
|targetRepo              | permet de définir le dépôt Artifactory cible pour le pipeline envp6  |targetRepo: "repo-releases"|
|applicationKey          | permet de définir la clé (groupId) de l'application pour le pipeline envp6  |applicationKey: "fr.ans"|
|labelParametreVersionApplicationYaml               | permet de définir le label à modifier pour changer la version de l'application pour le pipeline envp6 |labelParametreVersionApplicationYaml: "esignsante_distrib_version"|
|pathToApplicationYaml              | permet de définir le chemin du yml à modifier pour changer la version de l'application pour le pipeline envp6 |pathToApplicationYaml: "hieradata/enreg/application.yaml"|
|compteur          | permet de définir un compteur pour incrémenter les modifications du yml pour le pipeline envp6 |compteur: "true"|
|isMvnVerify          | permet d'ajouter le goal verify à mvn lors de l'analyse Sonarqube |isMvnVerify: "true"|

