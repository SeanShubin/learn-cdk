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

## CDK Questions
- How do I navigate from a particular ec2 instance type string (for example "t1.micro") in the management console, to the corresponding InstanceClass and InstanceSize in the CDK? 
  - Here is specifically where I am looking
    - from
      - https://us-west-1.console.aws.amazon.com/ec2/v2/home?region=us-west-1#InstanceTypes:
      - Amazon Management Console
      - EC2
      - Instance Types
      - Instance Type
    - to
      - https://repo.maven.apache.org/maven2/software/amazon/awscdk/aws-cdk-lib/2.9.0/aws-cdk-lib-2.9.0.jar
      - Maven Central
      - group software.amazon.awscdk
      - artifact aws-cdk-lib
      - package software.amazon.awscdk.services.ec2
      - classes InstanceClass and InstanceSize
- Is S3 and CloudFormationInit the right way to get files on to ec2 instances?
  - The InitSource.fromS3Object and Source.asset methods are pretty aggressive about zipping and unziping things without me telling them to
- I saw an example where someone installed a database server on ec2 in order to access RDS, is that right?
- Oddity installing mysql
  - this works
    - InitCommand.argvCommand(listOf("yum","install","-y","mysql"))
  - this does not work
    - InitPackage.yum("mysql")

## How to deal with secrets
I am trying to use CDK spin up a Java application on an EC2 instance that talks to RDS.
My initial strategy was to have CDK generate a secret and make both EC2 and RDS aware of that secret,
but after reading up on the CfnOutput, StringParameter, SecretStringGenerator, Credentials classes,
and noticing certain steps omitted from the documentation,
I am starting to think I am trying to solve the problem in a way that is not supported.
The things causing me trouble is the randomized letters appended to the secret name in a CDK stack,
and my apparent inability to get those names pushed to the EC2 instance by CfnOutput or StringParameter,
these seem to be for some other purpose.
My suspicion now is that the way to go is to generate the secrets in secrets manager manually,
then build knowledge of those secret names into my cdk stack.
Does this seem like I am on the right track?
Or is there some documentation I missed that I should be looking into?

I am creating an example of how to adapt a non-aws web application to run in an aws stack.
This is for training during onboarding, where the primary concern is introducing foundational concepts upon which real world problems can be solved.

The structure of my app so far:
  val vpc = createVpc(scope)
  val securityGroup = createSecurityGroup(scope, vpc)
  val databasePassword = createDatabasePassword(scope)
  val database = createDatabase(scope, vpc, securityGroup, databasePassword)
  val filesForEc2 = createFilesForEc2Bucket(scope)
  val ec2 = createEc2Instance(scope, vpc, securityGroup, filesForEc2, database, databasePassword)
  val websiteBucket = createWebsiteBucket(scope, ec2)

My current guess as to how to connect S3 to EC2, progressing through trial and error
  val isProxy = RoutingRuleCondition.builder().keyPrefixEquals("proxy/").build()
  val hostName = ec2.instance.attrPublicDnsName
  val websiteRoutingRule = RoutingRule.builder().condition(isProxy).hostName(hostName).build()

I am mainly keeping this scoped to office hours for now,
as although this work is important,
creating new training material is not as time sensitive that have a more immediate and noticeable effect on our end-user. 

## Manual frontend setup
- Check backend
  - http://54.177.109.107:8080/Health
- Check api gateway
  - https://4jpigzu55b.execute-api.us-west-1.amazonaws.com/proxy/Health
- Check cloudfront distribution
  - https://d1pxsu81x74urf.cloudfront.net/index.html
- Cloudfront
  - Create Origin
    - origin domain
      - 4jpigzu55b.execute-api.us-west-1.amazonaws.com
  - Create Behavior
    - path pattern
      - /proxy/*
    - origin
      - 4jpigzu55b.execute-api.us-west-1.amazonaws.com
  - General
    - custom ssl certificate
- Check proxy
  - https://d1pxsu81x74urf.cloudfront.net/proxy/Health
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
