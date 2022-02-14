package com.seanshubin.learn.cdk.domain

import software.amazon.awscdk.*
import software.amazon.awscdk.services.apigatewayv2.alpha.AddRoutesOptions
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpApi
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpMethod
import software.amazon.awscdk.services.apigatewayv2.alpha.IApi
import software.amazon.awscdk.services.apigatewayv2.integrations.alpha.HttpUrlIntegration
import software.amazon.awscdk.services.cloudfront.AllowedMethods
import software.amazon.awscdk.services.cloudfront.BehaviorOptions
import software.amazon.awscdk.services.cloudfront.Distribution
import software.amazon.awscdk.services.cloudfront.origins.HttpOrigin
import software.amazon.awscdk.services.cloudfront.origins.S3Origin
import software.amazon.awscdk.services.ec2.*
import software.amazon.awscdk.services.iam.ManagedPolicy
import software.amazon.awscdk.services.iam.Role
import software.amazon.awscdk.services.iam.ServicePrincipal
import software.amazon.awscdk.services.rds.Credentials
import software.amazon.awscdk.services.rds.DatabaseInstance
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine
import software.amazon.awscdk.services.route53.HostedZone
import software.amazon.awscdk.services.route53.HostedZoneProviderProps
import software.amazon.awscdk.services.s3.Bucket
import software.amazon.awscdk.services.s3.deployment.BucketDeployment
import software.amazon.awscdk.services.s3.deployment.Source
import software.amazon.awscdk.services.secretsmanager.Secret
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator
import software.constructs.Construct

class Runner : Runnable {
    object Names {
        private const val prefix = "LearnCdk"
        const val stackId = "${prefix}StackId"
        const val vpcId = "${prefix}VpcId"
        const val securityGroupId = "${prefix}SecurityGroupId"
        const val ec2InstanceId = "${prefix}Ec2Id"
        const val ec2InstanceName = "${prefix}Ec2InstanceName"
        const val databaseInstanceId = "${prefix}DatabaseInstanceId"
        const val databaseName = "${prefix}DatabaseName"
        const val s3BucketNameForEc2Files = "${prefix}BucketEc2Name"
        const val s3BucketNameForWebsite = "${prefix}BucketWebsiteName"
        const val s3BucketDeploymentNameForEc2Files = "${prefix}BucketDeployForEc2FilesName"
        const val s3BucketDeploymentNameForWebsite = "${prefix}BucketDeployForWebsiteName"
        const val publicSubnetName = "${prefix}PublicSubnetName"
        const val privateSubnetName = "${prefix}PrivateSubnetName"
        const val roleName = "${prefix}RoleName"
        const val keyName = "${prefix}KeyName"
        const val cloudfrontName = "${prefix}CloudfrontName"
        const val apiName = "${prefix}ApiName"
        const val urlIntegration = "${prefix}IntegrationName"
        const val distributionName = "${prefix}DistributionName"
        const val hostedZoneName = "${prefix}HostedZoneName"
        const val domainName = "pairwisevote.com"
    }

    val privateSubnets = SubnetSelection.builder()
        .subnetType(SubnetType.PRIVATE_ISOLATED)
        .build()
    val publicSubnets = SubnetSelection.builder()
        .subnetType(SubnetType.PUBLIC)
        .build()

    override fun run() {
        val app = App()
        val stackProps = StackProps.builder().build()
        val scope = Stack(app, Names.stackId, stackProps)
        createStack(scope)
        app.synth()
    }

    private fun createStack(scope: Construct) {
        val vpc = createVpc(scope)
        val securityGroup = createSecurityGroup(scope, vpc)
        val databasePassword = createDatabasePassword(scope)
        val database = createDatabase(scope, vpc, securityGroup, databasePassword)
        val bucketWithFilesForEc2 = createFilesForEc2Bucket(scope)
        val ec2 = createEc2Instance(scope, vpc, securityGroup, bucketWithFilesForEc2, database, databasePassword)
        val apiGateway = createApiGateway(scope, ec2)
        val bucketWithFilesForWebsite = createWebsiteBucket(scope, ec2)
        val cloudFrontDistribution = createCloudfrontDistribution(scope, bucketWithFilesForWebsite, apiGateway)
    }

    private fun createRoute53(scope: Construct) {
        val query = HostedZoneProviderProps.builder().domainName(Names.domainName).build()
        val zone = HostedZone.fromLookup(scope, Names.hostedZoneName, query)
//        val aliasTarget = ApiGatewayDomain
//        val recordTarget = RecordTarget.fromAlias(aliasTarget)
//        val aliasRecord = ARecord.Builder.create(scope, "").target(recordTarget)
    }

    private fun String.removePrefix(prefix:String):String {
        if(startsWith(prefix)){
            return substring(prefix.length)
        } else {
            throw RuntimeException("Target '$this' did not start with prefix '$prefix'")
        }
    }

    private fun createCloudfrontDistribution(scope: Construct, staticSiteBucket: Bucket, api: IApi): Distribution {
        val staticSiteOrigin = S3Origin.Builder.create(staticSiteBucket).build()
        val staticSiteBehavior = BehaviorOptions.builder()
            .allowedMethods(AllowedMethods.ALLOW_ALL)
            .origin(staticSiteOrigin)
            .build()
        val apiOrigin = HttpOrigin.Builder.create(api.apiEndpoint).build()
        val apiBehavior = BehaviorOptions.builder()
            .origin(apiOrigin)
            .build()
        val additionalBehaviors = mapOf<String, BehaviorOptions>(
            "/proxy/*" to apiBehavior
        )
        val distribution = Distribution.Builder.create(scope, Names.distributionName)
            .defaultBehavior(staticSiteBehavior)
//            .additionalBehaviors(additionalBehaviors)
            .build()
        return distribution
    }

    private fun createApiGateway(scope: Construct, ec2: Instance): IApi {
        val httpApi = HttpApi.Builder.create(scope, Names.apiName).build()
        val instancePublicIp = ec2.instancePublicIp
        val url = "http://$instancePublicIp:8080/{proxy}"
        val integration = HttpUrlIntegration.Builder.create(Names.urlIntegration, url)
            .method(HttpMethod.ANY)
            .build()
        val addRoutesOptions = AddRoutesOptions.builder()
            .methods(listOf(HttpMethod.ANY))
            .path("/proxy/{proxy+}")
            .integration(integration)
            .build()
        httpApi.addRoutes(addRoutesOptions)
        return httpApi
    }

    fun createDatabasePassword(scope: Construct): Secret {
        val secretStringGenerator = SecretStringGenerator.builder()
            .excludePunctuation(true)
            .build()
        val databasePassword = Secret.Builder.create(scope, "databasePassword")
            .generateSecretString(secretStringGenerator)
            .build()
        return databasePassword
    }

    fun createVpc(scope: Construct): Vpc {
        val publicSubnet = SubnetConfiguration.builder()
            .name(Names.publicSubnetName)
            .cidrMask(24)
            .subnetType(SubnetType.PUBLIC)
            .build()
        val privateSubnet = SubnetConfiguration.builder()
            .name(Names.privateSubnetName)
            .cidrMask(28)
            .subnetType(SubnetType.PRIVATE_ISOLATED)
            .build()
        val subnetList = listOf(publicSubnet, privateSubnet)
        val vpc = Vpc.Builder.create(scope, Names.vpcId)
            .cidr("10.0.0.0/16")
            .natGateways(0)
            .subnetConfiguration(subnetList)
            .build()
        return vpc
    }

    private fun createSecurityGroup(scope: Construct, vpc: Vpc): SecurityGroup {
        val securityGroup = SecurityGroup.Builder.create(scope, Names.securityGroupId)
            .allowAllOutbound(true)
            .vpc(vpc)
            .build()
        securityGroup.addIngressRule(
            Peer.anyIpv4(),
            Port.tcp(22),
            "Allow SSH from anywhere"
        )
        securityGroup.addIngressRule(
            Peer.anyIpv4(),
            Port.tcp(80),
            "Allow HTTP from anywhere"
        )
        securityGroup.addIngressRule(
            Peer.anyIpv4(),
            Port.tcp(8080),
            "Allow HTTP debug from anywhere"
        )
        securityGroup.addIngressRule(
            Peer.anyIpv4(),
            Port.tcp(443),
            "Allow HTTPS from anywhere"
        )
        securityGroup.addIngressRule(
            Peer.anyIpv4(),
            Port.tcp(3306),
            "Allow MYSQL from anywhere"
        )
        return securityGroup
    }

    private fun createWebsiteBucket(scope: Construct, ec2: Instance): Bucket {
        val bucket = Bucket.Builder.create(scope, Names.s3BucketNameForWebsite)
            .removalPolicy(RemovalPolicy.DESTROY)
            .autoDeleteObjects(true)
            .publicReadAccess(true)
            .build()
        val s3Files = Source.asset("generated/s3/website")
        val deploySources = listOf(s3Files)
        val bucketDeployment = BucketDeployment.Builder.create(scope, Names.s3BucketDeploymentNameForWebsite)
            .sources(deploySources)
            .destinationBucket(bucket)
            .build()
        return bucket
    }

    private fun createFilesForEc2Bucket(scope: Construct): Bucket {
        val bucket = Bucket.Builder.create(scope, Names.s3BucketNameForEc2Files)
            .removalPolicy(RemovalPolicy.DESTROY)
            .autoDeleteObjects(true)
            .build()
        val s3Files = Source.asset("generated/s3/ec2")
        val deploySources = listOf(s3Files)
        val bucketDeployment = BucketDeployment.Builder.create(scope, Names.s3BucketDeploymentNameForEc2Files)
            .sources(deploySources)
            .destinationBucket(bucket)
            .build()
        return bucket
    }

    private fun createEc2Instance(
        scope: Construct, vpc: Vpc,
        securityGroup: SecurityGroup,
        bucket: Bucket,
        database: DatabaseInstance,
        databasePassword: Secret
    ): Instance {
        val servicePrincipal = ServicePrincipal("ec2.amazonaws.com")
        val s3ReadOnlyAccess = ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess")
        val accessSecretsManager = ManagedPolicy.fromAwsManagedPolicyName("SecretsManagerReadWrite")
        val managedPolicyList = listOf(s3ReadOnlyAccess, accessSecretsManager)
        val role = Role.Builder.create(scope, Names.roleName)
            .assumedBy(servicePrincipal)
            .managedPolicies(managedPolicyList)
            .build()
        val computeInstanceType = InstanceType.of(
            InstanceClass.BURSTABLE2,
            InstanceSize.NANO
        )
        val machineImage = AmazonLinuxImage.Builder.create()
            .generation(AmazonLinuxGeneration.AMAZON_LINUX_2)
            .build()
        val executable = InitFileOptions.builder().group("ec2-user").owner("ec2-user").mode("000755").build()
        val userDir = InitCommandOptions.builder().cwd("/home/ec2-user/").build()
        val installJava = InitPackage.yum("java-17-amazon-corretto")
        val installMysql = InitCommand.argvCommand(listOf("yum", "install", "-y", "mysql"))
        val copyJavaArchiveForServer = InitSource.fromS3Object("/home/ec2-user", bucket, "backend.zip")
//        val copyEchoServerApp = InitSource.fromS3Object("/home/ec2-user", bucket, "echo.zip")
        val copySystemdEntry = InitSource.fromS3Object("/etc/systemd/system", bucket, "systemd.zip")
        val launchServer = InitCommand.argvCommand(listOf("systemctl", "start", "condorcet-backend"))
        val lines = listOf(
            "java -jar edit-json.jar configuration.json set string ${database.dbInstanceEndpointAddress} database host",
            "DATABASE_PASSWORD=\$(aws secretsmanager get-secret-value --region us-west-1 --output text --query SecretString --secret-id ${databasePassword.secretName})",
            "java -jar edit-json.jar secrets/secret-configuration.json set string \$DATABASE_PASSWORD database password"
        )
        val content = lines.joinToString("\n", "", "\n")
        val initializeContent = InitFile.fromString("/home/ec2-user/initialize.sh", content, executable)
        val chown = InitCommand.argvCommand(listOf("sudo", "chown", "-R", "ec2-user:ec2-user", "/home/ec2-user"))
        val initializeExec = InitCommand.shellCommand("./initialize.sh", userDir)
        val configElements = listOf(
            installJava,
            installMysql,
            copyJavaArchiveForServer,
//            copyEchoServerApp,
            copySystemdEntry,
            initializeContent,
            initializeExec,
            chown,
            launchServer
        )
        val initConfig = InitConfig(configElements)
        val cloudFormationInit = CloudFormationInit.fromConfig(initConfig)
        val ec2 = Instance.Builder.create(scope, Names.ec2InstanceId)
            .securityGroup(securityGroup)
            .vpc(vpc)
            .vpcSubnets(publicSubnets)
            .role(role)
            .instanceName(Names.ec2InstanceName)
            .instanceType(computeInstanceType)
            .machineImage(machineImage)
            .keyName(Names.keyName)
            .init(cloudFormationInit)
            .build()
        return ec2
    }

    private fun createDatabase(
        scope: Construct,
        vpc: Vpc,
        securityGroup: SecurityGroup,
        databasePassword: Secret
    ): DatabaseInstance {
        val securityGroups = listOf(securityGroup)
        val databaseInstanceType = InstanceType.of(
            InstanceClass.BURSTABLE2,
            InstanceSize.MICRO
        )
        val credentials = Credentials.fromPassword("root", databasePassword.secretValue)
        val database = DatabaseInstance.Builder.create(scope, Names.databaseInstanceId)
            .databaseName(Names.databaseName)
            .publiclyAccessible(true)
            .engine(DatabaseInstanceEngine.MYSQL)
            .credentials(Credentials.fromGeneratedSecret("root"))
            .vpc(vpc)
            .instanceType(databaseInstanceType)
            .vpcSubnets(publicSubnets)
            .port(3306)
            .credentials(credentials)
            .removalPolicy(RemovalPolicy.DESTROY)
            .deletionProtection(false)
            .securityGroups(securityGroups)
            .backupRetention(Duration.seconds(0))
            .build()
        return database
    }
}