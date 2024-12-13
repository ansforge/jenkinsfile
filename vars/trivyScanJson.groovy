import groovy.json.*

def call(def imageToScan, def params, def severity) {
    echo 'Début du scan Trivy'
    sh 'rm -rf reports'
    sh 'mkdir reports'
    sh "docker pull ${imageToScan}"
    echo 'Début du scan Trivy table'
    def trivyTableOutput = sh(script: "trivy image -d --db-repository registry.url.gouv.fr/ghcr.io/aquasecurity/trivy-db --insecure --offline-scan  --skip-update --severity ${severity} --format table ${params} ${imageToScan}", returnStdout: true).trim()
    // Save Trivy JSON report
    writeFile file: 'trivy_client_scan_report.txt', text: trivyTableOutput
    // Display Trivy scan summary
    echo "Trivy scan results for ${imageToScan}"
    println trivyTableOutput
    // Check if vulnerabilities were found
    def extractor = ''
    if (trivyTableOutput.contains('Total: 0')) {
        echo 'No vulnerabilities found in the Docker image.'
    } else {
        echo 'Vulnerabilities found in the Docker image.'
        extractor = sh(script: "sed -n '3p' trivy_client_scan_report.txt", returnStdout: true).trim()
        echo "ALPHA extractor ${extractor}"
    }
    echo 'Fin du scan Trivy json'

    sh "docker rmi ${imageToScan} --force"

    return "Résultats ${imageToScan} = ${extractor}"
}
