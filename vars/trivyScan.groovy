import groovy.json.*

def call(def imageToScan, def params, def severity) {
    echo 'Début du scan Trivy'
    sh 'rm -rf reports'
    sh 'mkdir reports'
    sh "docker pull ${imageToScan}"
    sh "trivy image -d --db-repository registry.url.gouv.fr/ghcr.io-cache/aquasecurity/trivy-db --insecure --offline-scan --skip-update --severity ${severity} --format template --template @/usr/local/share/trivy/templates/html.tpl -o reports/report.html ${params} ${imageToScan}"
    sh "docker rmi ${imageToScan} --force"
    publishHTML target : [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: 'reports',
        reportFiles: 'report.html',
        reportName: "Trivy Scan ${imageToScan}",
        reportTitles: 'Trivy Scan'
    ]
    echo 'Fin du scan Trivy'
}
