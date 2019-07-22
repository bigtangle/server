def product = 'bigtangle-server'
def default_recipients = 'cui@inasset.de, cui@bigtangle.net'
// Check required build parameters
//assert env.jenkinsNode?.trim(): 'Parameter jenkinsNode must be defined!'
//assert env.sonarUrl?.trim(): 'Parameter sonarUrl must be defined!'
//assert env.mavenRepository?.trim(): 'Parameter mavenRepository must be defined!'


node(env.jenkinsNode) {

    stage('Checkout') {

        try {
            checkout scm
        }
        catch (e) {
            echo "No Pipeline SCM build, using my repo"
            checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'c71af3e8-4b2a-40b6-b6f8-ab20d886a25e', url: 'git@gitlab.reisendeninfo.aws.db.de:pxr_rikern/as-vdv-aus.git']]])
        }


        def version = ''
        try {
            version = gitDescribe()
        }
        catch (e) {
            echo "No description (tag) in git, building SNAPSHOT"
            version = 'SNAPSHOT'
        }

        echo "Build for ${product} of version ${version}"
        currentBuild.displayName = "#${env.BUILD_NUMBER}@${version}"
    }

    stage('Compile') {
        try {
            sh "./gradlew clean classes"
        }
        catch(e) {
            emailext body: 'Stage compile failed $DEFAULT_CONTENT',
                recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
                ],
                replyTo: '$DEFAULT_REPLYTO',
                subject: 'Stage compile failed $DEFAULT_SUBJECT',
                to: '$DEFAULT_RECIPIENTS'
            currentBuild.result = 'FAILURE'
            error('Stopping early…')
        }
    }

    stage('Unit Test') {
        try {
            sh "./gradlew check"
        }
        catch(e) {
            emailext body: 'Unit tests failed $DEFAULT_CONTENT',
                recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
                ],
                replyTo: '$DEFAULT_REPLYTO',
                subject: 'Unit tests failed $DEFAULT_SUBJECT',
                to: '$DEFAULT_RECIPIENTS'
            currentBuild.result = 'FAILURE'
            error('Stopping early…')
        }
        finally {
            step([$class: 'JUnitResultArchiver', testResults: "**/build/test-results/test/*.xml", allowEmptyResults:true])
        }
    }

    stage('Metrics') {
        try {
            sh "./gradlew sonarqube -Dsonar.host.url=${env.sonarUrl}"
        }
        catch(e) {
            emailext body: 'Stage SONAR failed $DEFAULT_CONTENT',
                recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
                ],
                replyTo: '$DEFAULT_REPLYTO',
                subject: 'Stage SONAR failed $DEFAULT_SUBJECT',
                to: '$DEFAULT_RECIPIENTS'
            currentBuild.result = 'FAILURE'
            error('Stopping early…')
        }
    }

    stage('Create Docker container') {
        try {
            sh "./gradlew buildDockerImage"
        }
        catch(e) {
            emailext body: 'Stage build docker failed $DEFAULT_CONTENT',
                recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
                ],
                replyTo: '$DEFAULT_REPLYTO',
                subject: 'Stage build docker failed $DEFAULT_SUBJECT',
                to: '$DEFAULT_RECIPIENTS'
            currentBuild.result = 'FAILURE'
            error('Stopping early…')
        }
    }

    stage('Push Docker container') {
        try {
            sh "./gradlew pushDockerImage"
        }
        catch(e) {
            emailext body: 'Stage push container failed $DEFAULT_CONTENT',
                recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
                ],
                replyTo: '$DEFAULT_REPLYTO',
                subject: 'Stage push container failed $DEFAULT_SUBJECT',
                to: '$DEFAULT_RECIPIENTS'
            currentBuild.result = 'FAILURE'
            error('Stopping early…')
        }

    }

/*
    stage('Package') {
        sh './build-product.sh'
    }
*/

/*
    stage ('Acceptance Tests') {
    def testSuite = 'NSS.Active'
    echo "FitNesse Tests Suite ${testSuite} for ${product} of version ${version} in Docker Compose."
    def stageAt = load('stage.at.groovy')
    stageAt.runAcceptanceTest(testSuite)
    }
*/

    stage('Publish') {
        try {
            sh "./gradlew publish -PmavenSnapshotRepositoryUrl=${env.mavenRepository} -PmavenReleaseRepositoryUrl=${env.mavenRepository} -PmavenRepositoryUserName=deployment -PmavenRepositoryPassword=deployment123"
//        sh './publish-product.sh'
        }
        catch(e) {
            emailext body: 'Stage push artefacts failed $DEFAULT_CONTENT',
                recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
                ],
                replyTo: '$DEFAULT_REPLYTO',
                subject: 'Stage push artefacts failed $DEFAULT_SUBJECT',
                to: '$DEFAULT_RECIPIENTS'
        }
    }

    if (env.nextStage?.trim()) {
        stage 'Trigger Next Stage'
        build job: env.nextStage, parameters: nextStageParameters(version, product), wait: false
    }
}

def gitDescribe() {
    executeAndReturnStdout('git describe --tags')
}

def executeAndReturnStdout(command) {
    sh(returnStdout: true, script: command).trim()
}

def nextStageParameters(version, product) {
    def parameterName = "product_${product}".replace('-', '_')
    def parameterValue = getGradleTaskResult("artifactDef")
    return [[$class: 'StringParameterValue', name: 'version', value: version], [$class: 'StringParameterValue', name: parameterName, value: parameterValue]]
}

def getGradleTaskResult(task) {
    sh(script: "./gradlew -q ${task}", returnStdout: true).trim()
}
