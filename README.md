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



I see how to create the HttpOrigin, but not how to add it to the distribution like it was done in the AWS Management Console.   If you look at this example code (Kotlin using the Java API), you can see me creating the HttpOrigin just fine, but there does not appear to be a method on Distribution that allows an additional origin to be added.

import software.amazon.awscdk.services.apigatewayv2.alpha.IApi
import software.amazon.awscdk.services.s3.Bucket
import software.amazon.awscdk.services.cloudfront.Distribution

      private fun createCloudfrontDistribution(
            staticSiteBucket: Bucket,
            api: IApi
        ): Distribution {
            val staticSiteOrigin = S3Origin.Builder.create(staticSiteBucket).build()
            val staticSiteBehavior = BehaviorOptions.builder()
                .allowedMethods(AllowedMethods.ALLOW_ALL)
                .origin(staticSiteOrigin)
                .build()
            val httpOrigin = HttpOrigin.Builder.create(api.apiEndpoint).build()
            val distribution = Distribution.Builder.create(this, Names.distributionName)
                .defaultBehavior(staticSiteBehavior)
                .defaultRootObject("index.html")
                .build()
            return distribution
        }

This was done in cloudfront like this:
- Cloudfront
- Distributions
- Origins
- Create Origin
- Origin Domain
  - yggj9m1nhh.execute-api.us-west-1.amazonaws.com
- HTTPS only
- Create Origin

so I was expecting something like Distribution.addOrigin or Distribution.createOrigin

            <dependency>
                <groupId>software.amazon.awscdk</groupId>
                <artifactId>aws-cdk-lib</artifactId>
                <version>2.12.0</version>
            </dependency>
            <dependency>
                <groupId>software.amazon.awscdk</groupId>
                <artifactId>apigatewayv2-alpha</artifactId>
                <version>2.12.0-alpha.0</version>
            </dependency>
            <dependency>
                <groupId>software.amazon.awscdk</groupId>
                <artifactId>apigatewayv2-integrations</artifactId>
                <version>1.144.0</version>
            </dependency>
            <dependency>
                <groupId>software.amazon.awscdk</groupId>
                <artifactId>apigatewayv2-integrations-alpha</artifactId>
                <version>2.10.0-alpha.0</version>
            </dependency>
            <dependency>
                <groupId>software.constructs</groupId>
                <artifactId>constructs</artifactId>
                <version>10.0.62</version>
            </dependency>
