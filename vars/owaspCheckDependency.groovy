#!/usr/bin/env groovy

def call() {
    echo 'Beginning of Owasp Dependency Check'
    dependencyCheck additionalArguments: '--noupdate --data /var/tmp/dependency-check-data/ --disableCentral --disableAssembly --format XML --format HTML --format JSON  --disableOssIndex --disableRetireJS --log owasp.log --scan **/*.jar --scan **/*.war', odcInstallation: 'dependency-check-5.2.2'
    echo 'Ending of Owasp Dependency Check'
}
