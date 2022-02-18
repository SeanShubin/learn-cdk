# Learn CDK

## Adding a keypair
- Amazon Management Console
- EC2
- Key Pairs
- Create Key Pair
- name: LearnCdkKeyName
- download
- move to ./secrets/
- `chmod 400 secrets/LearnCdkKeyName.pem`
- `ssh -i "secrets/LearnCdkKeyName.pem" ec2-user@ec2-13-56-12-27.us-west-1.compute.amazonaws.com`

## Uploading files to EC2 from CDK
- Get the files from your local storage to an s3 bucket via BucketDeployment using Source.asset
- Get the files from your s3 bucket to your ec2 instance via CloudFormationInit using InitSource.fromS3Object 
- Source.asset will treat any file you specify as a .zip file and explode it during upload,
  so if you want to specify a single file, put it in its own directory or its own zip file
- InitSource.fromS3Object will treat the data at the specified key as a zip file,
  so be sure anything you want to treat as a single file is in its own directory or in its own zip file
- This can be particularly confusing when deploying java archive's as they will be exploded onto your ec2 instance if you did not put them in their own directory or zip file

## EC2 Commands
- scp -i secrets/LearnCdkKeyName.pem ../condorcet-backend/console/target/condorcet-backend-console.jar ec2-user@ec2-3-101-80-18.us-west-1.compute.amazonaws.com:
- sudo yum -y install java-17-amazon-corretto
- java -jar condorcet-backend-console.jar


- sudo yum -y install git
- sudo yum -y install java-17-amazon-corretto-headless
- cd /tmp
- sudo wget https://dlcdn.apache.org/maven/maven-3/3.8.4/binaries/apache-maven-3.8.4-bin.tar.gz
- sudo tar xf /tmp/apache-maven-*.tar.gz -C /opt
- sudo ln -s /opt/apache-maven-3.8.4 /opt/maven
sudo nano /etc/profile.d/maven.sh
/usr/lib/jvm/java-1.7.0-openjdk-1.7.0.261.x86_64

export JAVA_HOME=/usr/lib/jvm/java-1.7.0-openjdk-1.7.0.261.x86_64
export M2_HOME=/opt/maven
export MAVEN_HOME=/opt/maven
export PATH=${M2_HOME}/bin:${PATH}

sudo chmod +x /etc/profile.d/maven.sh
source /etc/profile.d/maven.sh

## Scripts
- `./scripts/build.sh` takes about 15 seconds
- `./scripts/deploy.sh` takes about 8.5 minutes
- `./scripts/teardown.sh` takes about 5 minutes
- `./scripts/teardown-build-deploy.sh` is the entire feedback loop, it takes about 12.5 minutes

## Research
- https://saravanastar.medium.com/deploy-a-spa-in-s3-backend-service-in-api-gateway-and-access-through-cloudfront-f3e681bfc7ab
- https://blog.phillipninan.com/provision-an-rds-instance-using-the-aws-cdk-and-secrets
- https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2_instance-profiles.html
- https://bobbyhadz.com/blog/aws-cdk-ec2-instance-example
- https://docs.aws.amazon.com/cdk/api/v1/docs/aws-ec2-readme.html
- https://bobbyhadz.com/blog/aws-cdk-rds-example
- https://medium.com/@benmorel/creating-a-linux-service-with-systemd-611b5c8b91d6

## Manual frontend setup
- Check backend
  - http://54.193.254.252:8080/Health
- Check api gateway
  - https://pop9lkgzfi.execute-api.us-west-1.amazonaws.com/proxy/Health
- Check cloudfront distribution
  - https://d5rpcdxkxxum4.cloudfront.net
- Cloudfront
  - Create Origin
    - origin domain
      - g0hi31us61.execute-api.us-west-1.amazonaws.com
    - HTTPS only
  - Create Behavior
    - path pattern
      - /proxy/*
    - origin
      - isi6u71w51.execute-api.us-west-1.amazonaws.com
    - Viewer Protocol Policy
      - HTTPS only
    - Allowed HTTP methods
      - GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE
    - Create origin request policy
      - AllCookies
    - Origin Request Policy
      - AllCookies
  - Error Pages
    - 403 -> /index.html
  - General
    - custom ssl certificate
- Check proxy
  - https://d1oxk1k5ecqui.cloudfront.net/proxy/Health
- Route53
  - Create Record
  - A
  - Alias
    - Alias to Cloudfront Distribution
  - d1jb0whapyks6i.cloudfront.net
- Check
  - Cloudfront
    - static
      - https://d1jb0whapyks6i.cloudfront.net/index.html
    - proxy
      - https://d1jb0whapyks6i.cloudfront.net/proxy/Health
  - Frontend
    - https://pairwisevote.com/index.html


## Todo
- load balancer
- autoscaling group
- vote.onboarding.cjpowered.com
- vote.cj.com


## Debugging

Here is my latest experiment, it looks like I can't get the domain name from the 
software.amazon.awscdk.services.apigatewayv2.alpha.HttpApi.
I have tried apiId, apiEndpoint, url, httpApiName, and httpApiId.
What I really need is somethingl like a "getDomain()" method, or way to get the same thing.  I tried writing code to strip the colon from the url, but that doesn't work because when the code is run, url is just an unreified token.
Code and results below:

---

class CurrentExperimentStack(
    scope: Construct,
    staticSiteBucket: Bucket,
    api: HttpApi
) : Stack(scope, Names.currentExperimentStackId) {
    val distribution = createCloudfrontDistribution(staticSiteBucket, api)
    private fun createCloudfrontDistribution(
        staticSiteBucket: Bucket,
        api: HttpApi
    ): Distribution {
        val staticSiteOrigin = S3Origin.Builder.create(staticSiteBucket).build()
        val httpOrigin = HttpOrigin
            .Builder
            .create(api.apiEndpoint)
            .build()
        val httpBehavior = BehaviorOptions.builder().origin(httpOrigin).build()
        val additionalBehaviors = mapOf(
            "/proxy/*" to httpBehavior
        )
        val staticSiteBehavior = BehaviorOptions.builder()
            .allowedMethods(AllowedMethods.ALLOW_ALL)
            .origin(staticSiteOrigin)
            .build()
        val distribution = Distribution.Builder.create(this, Names.distributionName)
            .defaultBehavior(staticSiteBehavior)
            .additionalBehaviors(additionalBehaviors)
            .defaultRootObject("index.html")
            .build()
        return distribution
    }
}

---

HttpOrigin.Builder.create(api.apiId).build()

Resource handler returned message: "Invalid request provided: The parameter origin name must be a domain name. (Service: CloudFront, Status Code: 400, Request ID: 2185b715-d7c0-4389-a68a-e0e712fb3d26, Extended Request ID: null)" (RequestToken: a9253d4f-3900-25f8-913e-ad670cfefa9b, HandlerErrorCode: InvalidRequest)

---

HttpOrigin.Builder.create(api.apiEndpoint).build()

Resource handler returned message: "Invalid request provided: The parameter origin name cannot contain a colon. (Service: CloudFront, Status Code: 400, Request ID: cb390c36-25f0-4bde-8e72-4a1d3c2a8b94, Extended Request ID: null)" (RequestToken: 1bc91dca-9d27-7db5-7080-c771e921ee0d, HandlerErrorCode: InvalidRequest)

---

HttpOrigin.Builder.create(api.url).build()

Resource handler returned message: "Invalid request provided: The parameter origin name cannot contain a colon. (Service: CloudFront, Status Code: 400, Request ID: 09e2fbe0-276a-438b-95ff-438dc64ea695, Extended Request ID: null)" (RequestToken: 8ced849c-c358-8cfa-d73d-e9a8cdbc024e, HandlerErrorCode: InvalidRequest)

---

HttpOrigin.Builder.create(api.httpApiName).build()

Resource handler returned message: "Invalid request provided: The parameter origin name must be a domain name. (Service: CloudFront, Status Code: 400, Request ID: cb937d14-bd00-40cd-b974-2f77d14cd873, Extended Request ID: null)" (RequestToken: 521569d0-7d4a-c445-365d-71b59587fcab, HandlerErrorCode: InvalidRequest)

---

HttpOrigin.Builder.create(api.httpApiId).build()

Resource handler returned message: "Invalid request provided: The parameter origin name must be a domain name. (Service: CloudFront, Status Code: 400, Request ID: c54eef0c-115d-426a-a941-78309c1c412d, Extended Request ID: null)" (RequestToken: 9e3774a4-99c0-5582-0733-e987ee900792, HandlerErrorCode: InvalidRequest)
