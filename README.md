# File Notification ENM stub

This is a temporary stub that will be used till the File Notification Service has been created.
The functionality includes uploading files to an SFTP server (with correct permissions and directory structure) as placed in the "files-to-upload" directory. The files in this directory are used as a dataset.
The numberOfNodes as set in the values.yaml determines the number of files uploaded to the sftp server. During the upload, the file path is updated to include a unique nodename and corresponding filename with the current rop time.
The numberOfNodes set to 100 will result in 100 5G event files and 100 PM Counter files (Mixed 4G & 5G) so a total of 200 files will be uploaded.
The service will automatically rename files on the server every 15 mins (clockwise) and send notifications to topic for downstream services to then use as a notification to process a file from the SFTP server.

SFTP Connection timeout and number of times to retry can be set in the values.yaml. This configuration allows the STUB to wait for an STFP-Server to come online before attempting to transfer any files and send any notifications. 
This is useful for APP Staging testing whereby an sftp server may be deployed after the microservices under test. 

## Contact Information
#### Team Members
##### Chassis
Team Quaranteam and Phoenix are currently developing this.
For support please contact Thomas Kelly <a href="mailto:thomas.kelly@ericsson.com"> thomas.kelly@ericsson.com</a> or Cyrille Tchouateu <a href="mailto:cyrille.tchouateu@ericsson.com"> cyrille.tchouateu@ericsson.com</a>

##### CI Pipeline
The CI Pipeline aspect of the Microservice Chassis is now owned, developed and maintained by [Team Hummingbirds](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/ACE/Hummingbirds+Home) in the DE (Development Environment) department of PDU OSS.

#### Email
Guardians for this project can be reached at Team Quaranteam <PDLDUNQUAR@pdl.internal.ericsson.com> or through the <a href="https://teams.microsoft.com/l/channel/19%3a9bc0c88ae51e4c77ae35092123673db8%40thread.skype/Developer%2520Experience?groupId=f7576b61-67d8-4483-afea-3f6e754486ed&tenantId=92e84ceb-fbfd-47ab-be52-080c6b87953f">ADP Developer Experience MS Teams Channel</a>

## Maven Dependencies
The chassis has the following Maven dependencies:
  - Spring Boot Start Parent version 2.5.2.
  - Spring Boot Starter Web.
  - Spring Boot Actuator.
  - Spring Cloud Sleuth.
  - Spring Boot Started Test.
  - JaCoCo Code Coverage Plugin.
  - Sonar Maven Plugin.
  - Spotify Dockerfile Maven Plugin.
  - Common Logging utility for logback created by Vortex team.
  - Properties for spring cloud version and java are as follows.
```
<version.spring-cloud>2020.0.3</version.spring-cloud>
```

## Build related artifacts
The main build tool is BOB provided by ADP. For convenience, maven wrapper is provided to allow the developer to build in an isolated workstation that does not have access to ADP.
  - [ruleset2.0.yaml](ruleset2.0.yaml) - for more details on BOB please see [Bob 2.0 User Guide](https://gerrit.ericsson.se/plugins/gitiles/adp-cicd/bob/+/refs/heads/master/USER_GUIDE_2.0.md). 
     You can also see an example of Bob usage in a Maven project in [BOB](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Adopting+BOB+Into+the+MVP+Project).
  - [precoderview.Jenkinsfile](precodereview.Jenkinsfile) - for pre code review Jenkins pipeline that runs when patch set is pushed.
  - [publish.Jenkinsfile](publish.Jenkinsfile) - for publish Jenkins pipeline that runs after patch set is merged to master.
  - [.bob.env](.bob.env) - if you are running Bob for the first time this file will not be available on your machine. 
    For more details on how to set it up please see [Bob 2.0 User Guide](https://gerrit.ericsson.se/plugins/gitiles/adp-cicd/bob/+/refs/heads/master/USER_GUIDE_2.0.md). 

If the developer wishes to manually build the application in the local workstation, the ```bob clean init-dev build image package-local``` command can be used once BOB is configured in the workstation.  
Note: The ```mvn clean install``` command will be required before running the bob command above.  
See the "Containerization and Deployment to Kubernetes cluster" section for more details on deploying the built application.

Stub jar files are necessary to allow contract tests to run. The stub jars are stored in JFrog (Artifactory).
To allow the contract test to access and retrieve the stub jars, the .bob.env file must be configured as follows.
```
SELI_ARTIFACTORY_REPO_USER=<LAN user id>
SELI_ARTIFACTORY_REPO_PASS=<JFrog encripted LAN PWD or API key>
HOME=<path containing .m2, e.g. /c/Users/<user>/>
```
To retrieve an encrypted LAN password or API key, login to [JFrog](https://arm.seli.gic.ericsson.se) and select "Edit Profile". 
For info in setting the .bob.env file see [Bob 2.0 User Guide](https://gerrit.ericsson.se/plugins/gitiles/adp-cicd/bob/+/refs/heads/master/USER_GUIDE_2.0.md).

## Containerization and Deployment to Kubernetes cluster.
Following artifacts contains information related to building a container and enabling deployment to a Kubernetes cluster:
- [charts](charts/) folder - used by BOB to lint, package and upload helm chart to helm repository.
  -  Once the project is built in the local workstation using the ```bob clean init-dev build image package-local``` command, a packaged helm chart is available in the folder ```.bob/eric-oss-file-notification-enm-stub-internal/``` folder. 
     This chart can be manually installed in Kubernetes using ```helm install``` command. [P.S. required only for Manual deployment from local workstation]
- [Dockerfile](Dockerfile) - used by Spotify dockerfile maven plugin to build docker image.
  - The base image for the chassis application is ```sles-jdk8``` available in ```armdocker.rnd.ericsson.se```.

## Source
The [src](src/) folder of the java project contains a core spring boot application, a controller for health check and an interceptor for helping with logging details like user name. 
The folder also contains corresponding java unit tests.

```
.
├── CONTRIBUTING.md
├── Dockerfile
├── README.md
├── VERSION_PREFIX
├── charts
│   └── eric-oss-file-notification-enm-stub
│       ├── Chart.yaml
│       ├── eric-product-info.yaml
│       ├── templates
│       │   ├── NOTES.txt
│       │   ├── _helpers.tpl
│       │   ├── deployment.yaml
│       │   ├── hpa.yaml
│       │   ├── ingress.yaml
│       │   ├── podDisruptionBudget.yaml
│       │   ├── rolebinding.yaml
│       │   ├── service.yaml
│       │   ├── serviceaccount.yaml
│       └── values.yaml
├── ci
│   └── scripts
│       └── metrics-checker.sh
├── common-properties.yaml
├── deployAndUpgrade.py
├── doc
│   ├── API_Documentation
│   │   └── API_Documentation.adoc
│   ├── Application_Developers_Guide
│   │   └── Application_Developers_Guide.adoc
│   ├── Release_Notes
│   │   └── Release_Notes.adoc
│   ├── Service_Deployment
│   │   └── Service_Deployment.adoc
│   ├── Service_Metrics
│   │   └── Service_Metrics.adoc
│   ├── Service_Overview
│   │   └── Service_Overview.adoc
│   ├── Software_Vendor_List
│   │   └── Software_Vendor_List.adoc
│   ├── Specific_FOSS
│   │   └── Specific_FOSS.adoc
│   ├── Test_Documentation
│   │   └── Test_Documentation.adoc
│   ├── Test_Report
│   │   └── Test_Report.adoc
│   ├── Test_Specification
│   │   └── Test_Specification.adoc
│   ├── Trade_Compliance
│   │   └── Trade_Compliance.adoc
│   ├── Trademark_Information
│   │   └── Trademark_Information.adoc
│   ├── Troubleshooting_Guide
│   │   └── Troubleshooting_Guide.adoc
│   ├── make_docs.sh
│   └── parameters.adoc
├── files-to-upload
├── healthcheck.sh
├── license
│   ├── copyright-2020.txt
│   ├── copyright-2021.txt
│   ├── javaHeaderDefinition.xml
│   └── xmlHeaderDefinition.xml
├── lombok.config
├── pom.xml
├── precodereview.Jenkinsfile
├── publish.Jenkinsfile
├── ruleset2.0.yaml
└── src
    ├── main
    │   ├── java
    │   │   ├── META-INF
    │   │   │   └── MANIFEST.MF
    │   │   └── com
    │   │       └── ericsson
    │   │           └── oss
    │   │               └── adc
    │   │                   ├── CoreApplication.java
    │   │                   ├── client
    │   │                   │   └── example
    │   │                   │       ├── SampleRestClient.java
    │   │                   │       └── package-info.java
    │   │                   ├── config
    │   │                   │   └── SftpConfig.java
    │   │                   ├── controller
    │   │                   │   ├── example
    │   │                   │   │   ├── SampleApiControllerImpl.java
    │   │                   │   │   └── package-info.java
    │   │                   │   ├── health
    │   │                   │   │   ├── HealthCheck.java
    │   │                   │   │   └── package-info.java
    │   │                   │   └── package-info.java
    │   │                   ├── package-info.java
    │   │                   └── services
    │   │                       └── SftpService.java
    │   └── resources
    │       ├── application.yaml
    │       ├── bootstrap.yml
    │       ├── checkstyle.xml
    │       ├── jmx
    │       │   ├── jmxremote.access
    │       │   └── jmxremote.password
    │       ├── logback-json.xml
    │       └── v1
    │           ├── eric-oss-file-notification-enm-stub-openapi.yaml
    │           └── index.html
    └── test
        ├── java
        │   └── com
        │       └── ericsson
        │           └── oss
        │               └── adc
        │                   ├── CoreApplicationTest.java
        │                   ├── api
        │                   │   └── contract
        │                   │       ├── SampleApiBase.java
        │                   │       └── package-info.java
        │                   ├── business
        │                   │   └── package-info.java
        │                   ├── client
        │                   │   └── example
        │                   │       └── SampleRestClientTest.java
        │                   ├── controller
        │                   │   ├── example
        │                   │   │   ├── SampleApiControllerTest.java
        │                   │   │   └── package-info.java
        │                   │   └── health
        │                   │       ├── HealthCheckTest.java
        │                   │       └── package-info.java
        │                   ├── package-info.java
        │                   └── repository
        │                       └── package-info.java
        └── resources
            ├── META-INF
            │   └── MANIFEST.MF
            ├── contracts
            │   └── SampleContract.yaml
            └── install_sftp_server.sh

59 directories, 80 files

```


## Setting up CI Pipeline
-  Docker Registry is used to store and pull Docker images. At Ericsson official chart repository is maintained at the org-level JFrog Artifactory. 
   Follow the link to set up a [Docker registry](https://confluence.lmera.ericsson.se/pages/viewpage.action?spaceKey=ACD&title=How+to+create+new+docker+repository+in+ARM+artifactory).
-  Helm repo is a location where packaged charts can be stored and shared. The official chart repository is maintained at the org-level JFrog Artifactory. 
   Follow the link to set up a [Helm repo](https://confluence.lmera.ericsson.se/display/ACD/How+to+setup+Helm+repositories+for+ADP+e2e+CICD).
-  Follow instructions at [Jenkins Pipeline setup](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Microservice+Chassis+CI+Pipeline+Start+Guide#MicroserviceChassisCIPipelineStartGuide-JenkinsPipelinesetup)
   to use out-of-box Jenkinsfiles which comes along with eric-oss-file-notification-enm-stub.
-  Jenkins Setup involves master and agent machines. If there is not any Jenkins master setup, follow instructions at [Jenkins Master](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Microservice+Chassis+CI+Pipeline+Start+Guide#MicroserviceChassisCIPipelineStartGuide-JenkinsMaster-2.89.2(FEMJenkins)) - 2.89.2 (FEM Jenkins).
-  Request a node from the GIC (Note: RHEL 7 GridEngine Nodes have been successfully tested).
   [Request Node](https://estart.internal.ericsson.com/).
-  To setup [Jenkins Agent](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Microservice+Chassis+CI+Pipeline+Start+Guide#MicroserviceChassisCIPipelineStartGuide-Prerequisites) 
   for Jenkins, jobs execution follow the instructions at Jenkins Agent Setup.
-  The provided ruleset is designed to work in standard environments, but in case you need, you can fine tune the automatically generated ruleset to adapt to your project needs. 
   Take a look at [Bob 2.0 User Guide](https://gerrit.ericsson.se/plugins/gitiles/adp-cicd/bob/+/refs/heads/master/USER_GUIDE_2.0.md) for details about ruleset configuration.
    
   [Gerrit Repos](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Design+and+Development+Environment)  
   [BOB](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Adopting+BOB+Into+the+MVP+Project)  
   [Bob 2.0 User Guide](https://gerrit.ericsson.se/plugins/gitiles/adp-cicd/bob/+/refs/heads/master/USER_GUIDE_2.0.md)  
   [Docker registry](https://confluence.lmera.ericsson.se/pages/viewpage.action?spaceKey=ACD&title=How+to+create+new+docker+repository+in+ARM+artifactory)  
   [Helm repo](https://confluence.lmera.ericsson.se/display/ACD/How+to+setup+Helm+repositories+for+ADP+e2e+CICD)  
   [Jenkins Master](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Microservice+Chassis+CI+Pipeline+Start+Guide#MicroserviceChassisCIPipelineStartGuide-JenkinsMaster-2.89.2(FEMJenkins))  
   [Jenkins Agent](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Microservice+Chassis+CI+Pipeline+Start+Guide#MicroserviceChassisCIPipelineStartGuide-Prerequisites)  
   [Jenkins Pipeline setup](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Microservice+Chassis+CI+Pipeline+Start+Guide#MicroserviceChassisCIPipelineStartGuide-JenkinsPipelinesetup)  
   [EO Common Logging](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/ESO/EO+Common+Logging+Library)  
   [SLF4J](https://logging.apache.org/log4j/2.x/log4j-slf4j-impl/index.html)  
   [JFrog](https://arm.seli.gic.ericsson.se)  
   [Request Node](https://estart.internal.ericsson.com/)

## Using the Helm Repo API Token
The Helm Repo API Token is usually set using credentials on a given Jenkins FEM.
If the project you are developing is part of IDUN/Aeonic this will be pre-configured for you.
However, if you are developing an independent project please refer to the 'Helm Repo' section:
[Repository for ENM File Notification Stub Service CI Pipeline Guide](https://confluence-oss.seli.wh.rnd.internal.ericsson.com/display/PCNCG/Microservice+Chassis+CI+Pipeline+Start+Guide#MicroserviceChassisCIPipelineStartGuide-HelmRepo)

Once the Helm Repo API Token is made available via the Jenkins job credentials the precodereview and publish Jenkins jobs will accept the credentials (ex. HELM_SELI_REPO_API_TOKEN' or 'HELM_SERO_REPO_API_TOKEN) and create a variable HELM_REPO_API_TOKEN which is then used by the other files.

Credentials refers to a user or a functional user. This user may have access to multiple Helm repos.
In the event where you want to change to a different Helm repo, that requires a different access rights, you will need to update the set credentials.

## Artifactory Set-up Explanation
The Repository for ENM File Notification Stub Service Artifactory repos (dev, ci-internal and drop) are set up following the ADP principles: [ADP Repository Principles](https://confluence.lmera.ericsson.se/pages/viewpage.action?spaceKey=AA&title=2+Repositories)

The commands: "bob init-dev build image package" will ensure that you are pushing a Docker image to:
[Docker registry - Dev](https://arm.seli.gic.ericsson.se/artifactory/docker-v2-global-local/proj-eric-oss-dev/)

The Precodereview Jenkins job pushes a Docker image to:
[Docker registry - CI Internal](https://arm.seli.gic.ericsson.se/artifactory/docker-v2-global-local/proj-eric-oss-ci-internal/)

This is intended behaviour which mimics the behavior of the Publish Jenkins job.
This job presents what will happen when the real microservice image is being pushed to the drop repository.
Furthermore, the 'Helm Install' stage needs a Docker image which has been previously uploaded to a remote repository, hence why making a push to the CI Internal is necessary.

The Publish job also pushes to the CI-Internal repository, however the Publish stage promotes the Docker image and Helm chart to the drop repo:
[Docker registry - Drop](https://arm.seli.gic.ericsson.se/artifactory/docker-v2-global-local/proj-eric-oss-drop/)

Similarly, the Helm chart is being pushed to three separate repositories:
[Helm registry - Dev](https://arm.seli.gic.ericsson.se/artifactory/proj-eric-oss-dev-helm/)

The Precodereview Jenkins job pushes the Helm chart to:
[Helm registry - CI Internal](https://arm.seli.gic.ericsson.se/artifactory/proj-eric-oss-ci-internal-helm/)

This is intended behaviour which mimics the behavior of the Publish Jenkins job.
This job presents what will happen when the real Helm chart is being pushed to the drop repository.
The Publish Jenkins job pushes the Helm chart to:
[Helm registry - Drop](https://arm.seli.gic.ericsson.se/artifactory/proj-eric-oss-drop-helm/)
