pipeline {
    agent any

    parameters {
        choice(
            name: 'ENV',
            choices: ['SIT1','QA1','UAT1','HF1'],
            description: 'Choose the environment to run the test suite on.'
        )

        choice(
            name: 'SANITY_TYPE',
            choices: ['Basic', 'Extended'],
            description: 'Choose out of short sanity or extended sanity.'
        )

        string(
            name: 'TESTER',
            description: 'Enter your name.'
        )
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build SanityRunner') {
            steps {
                dir('java/local') {
                    bat 'mvn clean package'
                }
            }
        }

        stage('Run Tests & Generate Reports') {
            steps {
                sh 'scripts/pipeline.sh'
            }
        }
    }

    post {
        always {
            script {
                def reportContent = "Error: Report Not Found!"
            
                if (fileExists("${env.BUILD_NUMBER}/summary-report.html")) {
                    reportContent = readFile("${env.BUILD_NUMBER}/summary-report.html")
                } 

                emailext(
                    subject: "${env.JOB_NAME} - #${params.ENV} Build #${env.BUILD_NUMBER}",
                    from: "jenkins@mwhlvchcatools01",
                    to: "AQE-OffShoreGTM_Testing@int.amdocs.com",
                    replyTo: "kartikve@amdocs.com",
                    body: readFile("${env.BUILD_NUMBER}/summary-report.html"),
                    mimeType: 'text/html',
                    attachmentsPattern: "${env.BUILD_NUMBER}/*.*"
                )
            }
        }
    }
}
