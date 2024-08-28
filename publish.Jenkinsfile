#!/usr/bin/env groovy

def defaultBobImage = 'armdocker.rnd.ericsson.se/proj-adp-cicd-drop/bob.2.0:1.7.0-55'
def bob = new BobCommand()
    .bobImage(defaultBobImage)
    .envVars([
        HOME:'${HOME}',
        ISO_VERSION:'${ISO_VERSION}',
        RELEASE:'${RELEASE}',
        SONAR_HOST_URL:'${SONAR_HOST_URL}',
        SONAR_AUTH_TOKEN:'${SONAR_AUTH_TOKEN}',
        GERRIT_CHANGE_NUMBER:'${GERRIT_CHANGE_NUMBER}',
        KUBECONFIG:'${KUBECONFIG}',
        K8S_NAMESPACE: '${K8S_NAMESPACE}',
        USER:'${USER}',
        SELI_ARTIFACTORY_REPO_USER:'${CREDENTIALS_SELI_ARTIFACTORY_USR}',
        SELI_ARTIFACTORY_REPO_PASS:'${CREDENTIALS_SELI_ARTIFACTORY_PSW}',
        SERO_ARTIFACTORY_REPO_USER:'${CREDENTIALS_SERO_ARTIFACTORY_USR}',
        SERO_ARTIFACTORY_REPO_PASS:'${CREDENTIALS_SERO_ARTIFACTORY_PSW}',
        MAVEN_CLI_OPTS: '${MAVEN_CLI_OPTS}',
        OPEN_API_SPEC_DIRECTORY: '${OPEN_API_SPEC_DIRECTORY}'
    ])
    .needDockerSocket(true)
    .toString()

def LOCKABLE_RESOURCE_LABEL = "kaas"

def validateSdk = 'false'

pipeline {
    agent {
        node {
            label NODE_LABEL
        }
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '50'))
    }

    environment {
        RELEASE = "true"
        TEAM_NAME = "Team Quaranteam"
        KUBECONFIG = "${WORKSPACE}/.kube/config"
        CREDENTIALS_SELI_ARTIFACTORY = credentials('SELI_ARTIFACTORY')
        CREDENTIALS_SERO_ARTIFACTORY = credentials('SERO_ARTIFACTORY')
        MAVEN_CLI_OPTS = "-Duser.home=${env.HOME} -B -s ${env.WORKSPACE}/settings.xml"
        OPEN_API_SPEC_DIRECTORY = "src/main/resources/v1"
    }

    // Stage names (with descriptions) taken from ADP Microservice CI Pipeline Step Naming Guideline: https://confluence.lmera.ericsson.se/pages/viewpage.action?pageId=122564754
    stages {
        stage('Clean') {
            steps {
                echo 'Inject settings.xml into workspace:'
                configFileProvider([configFile(fileId: "${env.SETTINGS_CONFIG_FILE_NAME}", targetLocation: "${env.WORKSPACE}")]) {}
                archiveArtifacts allowEmptyArchive: true, artifacts: 'ruleset2.0.yaml, publish.Jenkinsfile'
                sh "${bob} clean"
            }
        }

        stage('Init') {
            steps {
                sh "${bob} init-drop"
                archiveArtifacts 'artifact.properties'
                script {
                    authorName = sh(returnStdout: true, script: 'git show -s --pretty=%an')
                    currentBuild.displayName = currentBuild.displayName + ' / ' + authorName
                }
            }
        }

        stage('Lint') {
            steps {
                parallel(
                    "lint markdown": {
                        sh "${bob} lint:markdownlint lint:vale"
                    },
                    "lint helm": {
                        sh "${bob} lint:helm"
                    },
                    "lint helm design rule checker": {
                        sh "${bob} lint:helm-chart-check"
                    },
//This check requires 'copyright' to be included in the pm counter xml files, have to disable it here.
//                    "lint code": {
//                        sh "${bob} lint:license-check"
//                    },
                    "lint OpenAPI spec": {
                        sh "${bob} lint:oas-bth-linter"
                    },
                    "lint metrics": {
                        sh "${bob} lint:metrics-check"
                    },
                    "SDK Validation": {
                        script {
                            if (validateSdk == "true") {
                                sh "${bob} validate-sdk"
                            }
                        }
                    }
                )
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: '**/*bth-linter-output.html, **/design-rule-check-report.*'
                }
            }
        }

        stage('Generate') {
            steps {
                sh "${bob} generate-docs"
                archiveArtifacts 'build/doc/**/*.*'
                publishHTML (target: [
                    allowMissing: false,
                    alwaysLinkToLastBuild: false,
                    keepAll: true,
                    reportDir: 'build/doc',
                    reportFiles: 'CTA_api.html',
                    reportName: 'REST API Documentation'
                ])
            }
        }

        stage('Build') {
            steps {
                sh "${bob} build"
            }
        }
        stage('Test') {
            steps {
                sh "${bob} test"
            }
        }

        stage('SonarQube') {
            when {
                expression { env.SQ_ENABLED == "true" }
            }
            steps {
                withSonarQubeEnv("${env.SQ_SERVER}") {
                    sh "${bob} sonar-enterprise-release"
                }
            }
        }

        stage('Image') {
            steps {
                sh "${bob} image"
                sh "${bob} image-dr-check"
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: '**/image-design-rule-check-report*'
                }
            }
        }

        stage('Package') {
            steps {
                script {
                    sh "${bob} package"
                    sh "${bob} package-jars"
                }
            }
        }

        stage('K8S Resource Lock') {
            options {
                lock(label: LOCKABLE_RESOURCE_LABEL, variable: 'RESOURCE_NAME', quantity: 1)
            }
            environment {
                K8S_CLUSTER_ID = sh(script: "echo \${RESOURCE_NAME} | cut -d'_' -f1", returnStdout: true).trim()
                K8S_NAMESPACE = sh(script: "echo \${RESOURCE_NAME} | cut -d',' -f1 | cut -d'_' -f2", returnStdout: true).trim()
            }
            stages {
                stage('Helm Install') {
                    steps {
                        echo "Inject kubernetes config file (${env.K8S_CLUSTER_ID}) based on the Lockable Resource name: ${env.RESOURCE_NAME}"
                        configFileProvider([configFile(fileId: "${K8S_CLUSTER_ID}", targetLocation: "${env.KUBECONFIG}")]) {}
                        echo "The namespace (${env.K8S_NAMESPACE}) is reserved and locked based on the Lockable Resource name: ${env.RESOURCE_NAME}"

                        sh "${bob} helm-dry-run"
                        sh "${bob} create-namespace"

                        script {
                            if (env.HELM_UPGRADE == "true") {
                                echo "HELM_UPGRADE is set to true:"
                                sh "${bob} helm-upgrade"
                            } else {
                                echo "HELM_UPGRADE is NOT set to true:"
                                sh "${bob} helm-install"
                            }
                        }

                        sh "${bob} healthcheck"
                    }
                    post {
                        always {
                            sh "${bob} kaas-info || true"
                            archiveArtifacts allowEmptyArchive: true, artifacts: 'build/kaas-info.log'
                        }
                        unsuccessful {
                            sh "${bob} collect-k8s-logs || true"
                            archiveArtifacts allowEmptyArchive: true, artifacts: 'k8s-logs/*'
                            sh "${bob} delete-namespace"
                        }
                    }
                }

                stage('K8S Test') {
                    steps {
                        sh "${bob} helm-test"
                    }
                    post {
                        unsuccessful {
                            sh "${bob} collect-k8s-logs || true"
                            archiveArtifacts allowEmptyArchive: true, artifacts: 'k8s-logs/*'
                        }
                        cleanup {
                            sh "${bob} delete-namespace"
                        }
                    }
                }
            }
        }

        stage('Publish') {
            steps {
                sh "${bob} publish"
            }
        }
    }
    post {
        success {
            script {
                bumpVersionPrefixPatch()
                sh "${bob} helm-chart-check-report-warnings"
                sendHelmDRWarningEmail()
                modifyBuildDescription()
            }
        }
    }
}

def modifyBuildDescription() {

    def CHART_NAME = "eric-oss-file-notification-enm-stub"
    def DOCKER_IMAGE_NAME = "eric-oss-file-notification-enm-stub"

    def VERSION = readFile('.bob/var.version').trim()

    def CHART_DOWNLOAD_LINK = "https://arm.seli.gic.ericsson.se/artifactory/proj-eric-oss-drop-helm/${CHART_NAME}/${CHART_NAME}-${VERSION}.tgz"
    def DOCKER_IMAGE_DOWNLOAD_LINK = "https://armdocker.rnd.ericsson.se/artifactory/docker-v2-global-local/proj-eric-oss-drop/${CHART_NAME}/${VERSION}/"

    currentBuild.description = "Helm Chart: <a href=${CHART_DOWNLOAD_LINK}>${CHART_NAME}-${VERSION}.tgz</a><br>Docker Image: <a href=${DOCKER_IMAGE_DOWNLOAD_LINK}>${DOCKER_IMAGE_NAME}-${VERSION}</a><br>Gerrit: <a href=${env.GERRIT_CHANGE_URL}>${env.GERRIT_CHANGE_URL}</a> <br>"
}

def sendHelmDRWarningEmail() {
    def val = readFile '.bob/var.helm-chart-check-report-warnings'
    if (val.trim().equals("true")) {
        echo "WARNING: One or more Helm Design Rules have a WARNING state. Review the Archived Helm Design Rule Check Report: design-rule-check-report.html"
        manager.addWarningBadge("One or more Helm Design Rules have a WARNING state. Review the Archived Helm Design Rule Check Report: design-rule-check-report.html")
        echo "Sending an email to Helm Design Rule Check distribution list: ${env.HELM_DR_CHECK_DISTRIBUTION_LIST}"
        try {
            mail to: "${env.HELM_DR_CHECK_DISTRIBUTION_LIST}",
            from: "${env.GERRIT_PATCHSET_UPLOADER_EMAIL}",
            cc: "${env.GERRIT_PATCHSET_UPLOADER_EMAIL}",
            subject: "[${env.JOB_NAME}] One or more Helm Design Rules have a WARNING state. Review the Archived Helm Design Rule Check Report: design-rule-check-report.html",
            body: "One or more Helm Design Rules have a WARNING state. <br><br>" +
            "Please review Gerrit and the Helm Design Rule Check Report: design-rule-check-report.html: <br><br>" +
            "&nbsp;&nbsp;<b>Gerrit master branch:</b> https://gerrit.ericsson.se/gitweb?p=${env.GERRIT_PROJECT}.git;a=shortlog;h=refs/heads/master <br>" +
            "&nbsp;&nbsp;<b>Helm Design Rule Check Report:</b> ${env.BUILD_URL}artifact/.bob/design-rule-check-report.html <br><br>" +
            "For more information on the Design Rules and ADP handling process please see: <br>" +
            "&nbsp;&nbsp; - <a href='https://confluence.lmera.ericsson.se/display/AA/Helm+Chart+Design+Rules+and+Guidelines'>Helm Design Rule Guide</a><br>" +
            "&nbsp;&nbsp; - <a href='https://confluence.lmera.ericsson.se/display/ACD/Design+Rule+Checker+-+How+DRs+are+checked'>More Details on Design Rule Checker</a><br>" +
            "&nbsp;&nbsp; - <a href='https://confluence.lmera.ericsson.se/display/AA/General+Helm+Chart+Structure'>General Helm Chart Structure</a><br><br>" +
            "<b>Note:</b> This mail was automatically sent as part of the following Jenkins job: ${env.BUILD_URL}",
            mimeType: 'text/html'
        } catch(Exception e) {
            echo "Email notification was not sent."
            print e
        }
    }
}

def bumpVersionPrefixPatch() {
    env.oldPatchVersionPrefix = readFile "VERSION_PREFIX"
    env.VERSION_PREFIX_CURRENT = env.oldPatchVersionPrefix.trim()

    sh 'docker run --rm -v $PWD/VERSION_PREFIX:/app/VERSION -w /app --user $(id -u):$(id -g) armdocker.rnd.ericsson.se/proj-eric-oss-drop/utilities/bump patch'

    env.newPatchVersionPrefix = readFile "VERSION_PREFIX"
    env.VERSION_PREFIX_UPDATED = env.newPatchVersionPrefix.trim()

    if (env.PUSH_VERSION_PREFIX_FILE == "true") {
        echo "VERSION_PREFIX has been bumped from ${VERSION_PREFIX_CURRENT} to ${VERSION_PREFIX_UPDATED}"

        sh """
            git add VERSION_PREFIX
            git commit -m "[ci-skip] Automatic new patch version bumping: ${VERSION_PREFIX_UPDATED}"
            git push origin HEAD:master
        """
    }
}

// More about @Builder: http://mrhaki.blogspot.com/2014/05/groovy-goodness-use-builder-ast.html
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

@Builder(builderStrategy = SimpleStrategy, prefix = '')
class BobCommand {
    def bobImage = 'bob.2.0:latest'
    def envVars = [:]
    def needDockerSocket = false

    String toString() {
        def env = envVars
                .collect({ entry -> "-e ${entry.key}=\"${entry.value}\"" })
                .join(' ')

        def cmd = """\
            |docker run
            |--init
            |--rm
            |--workdir \${PWD}
            |--user \$(id -u):\$(id -g)
            |-v \${PWD}:\${PWD}
            |-v /etc/group:/etc/group:ro
            |-v /etc/passwd:/etc/passwd:ro
            |-v /proj/mvn/:/proj/mvn
            |-v \${HOME}:\${HOME}
            |${needDockerSocket ? '-v /var/run/docker.sock:/var/run/docker.sock' : ''}
            |${env}
            |\$(for group in \$(id -G); do printf ' --group-add %s' "\$group"; done)
            |--group-add \$(stat -c '%g' /var/run/docker.sock)
            |${bobImage}
            |"""
        return cmd
                .stripMargin()           // remove indentation
                .replace('\n', ' ')      // join lines
                .replaceAll(/[ ]+/, ' ') // replace multiple spaces by one
    }
}
