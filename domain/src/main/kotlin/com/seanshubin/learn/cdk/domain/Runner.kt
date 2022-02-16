package com.seanshubin.learn.cdk.domain

import software.amazon.awscdk.*
import software.amazon.awscdk.services.apigatewayv2.alpha.AddRoutesOptions
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpApi
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpMethod
import software.amazon.awscdk.services.apigatewayv2.alpha.IApi
import software.amazon.awscdk.services.apigatewayv2.integrations.alpha.HttpUrlIntegration
import software.amazon.awscdk.services.cloudfront.*
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
        const val vpcStackId = "${prefix}VpcStackId"
        const val databaseStackId = "${prefix}DatabaseStackId"
        const val appStackId = "${prefix}AppStackId"
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

    class VpcStack(scope: Construct) : Stack(scope, Names.vpcStackId) {
        val vpc: Vpc = createVpc()
        val securityGroup: SecurityGroup = createSecurityGroup(vpc)
        val databasePassword: Secret = createDatabasePassword()
        fun createVpc(): Vpc {
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
            val vpc = Vpc.Builder.create(this, Names.vpcId)
                .cidr("10.0.0.0/16")
                .natGateways(0)
                .subnetConfiguration(subnetList)
                .build()
            return vpc
        }

        private fun createSecurityGroup(vpc: Vpc): SecurityGroup {
            val securityGroup = SecurityGroup.Builder.create(this, Names.securityGroupId)
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

        fun createDatabasePassword(): Secret {
            val secretStringGenerator = SecretStringGenerator.builder()
                .excludePunctuation(true)
                .build()
            val databasePassword = Secret.Builder.create(this, "databasePassword")
                .generateSecretString(secretStringGenerator)
                .build()
            return databasePassword
        }
    }

    class DatabaseStack(
        scope: Construct,
        subnetSelection: SubnetSelection,
        vpc: Vpc,
        securityGroup: SecurityGroup,
        databasePassword: Secret
    ) : Stack(scope, Names.databaseStackId) {
        val database: DatabaseInstance = createDatabase(vpc, securityGroup, databasePassword, subnetSelection)
        private fun createDatabase(
            vpc: Vpc,
            securityGroup: SecurityGroup,
            databasePassword: Secret,
            subnetSelection: SubnetSelection
        ): DatabaseInstance {
            val securityGroups = listOf(securityGroup)
            val databaseInstanceType = InstanceType.of(
                InstanceClass.BURSTABLE2,
                InstanceSize.MICRO
            )
            val credentials = Credentials.fromPassword("root", databasePassword.secretValue)
            val database = DatabaseInstance.Builder.create(this, Names.databaseInstanceId)
                .databaseName(Names.databaseName)
                .publiclyAccessible(true)
                .engine(DatabaseInstanceEngine.MYSQL)
                .credentials(Credentials.fromGeneratedSecret("root"))
                .vpc(vpc)
                .instanceType(databaseInstanceType)
                .vpcSubnets(subnetSelection)
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

    class ApplicationStack(
        scope: Construct,
        vpc: Vpc,
        securityGroup: SecurityGroup,
        database: DatabaseInstance,
        databasePassword: Secret,
        subnetSelection: SubnetSelection
    ) : Stack(scope, Names.appStackId) {
        val bucketWithFilesForEc2 = createFilesForEc2Bucket()
        val ec2 = createEc2Instance(
            vpc,
            securityGroup,
            bucketWithFilesForEc2,
            database,
            databasePassword,
            subnetSelection
        )
        val apiGateway = createApiGateway(ec2)
        val bucketWithFilesForWebsite = createWebsiteBucket(ec2)
        val cloudFrontDistribution = createCloudfrontDistribution(bucketWithFilesForWebsite, apiGateway)

        private fun createFilesForEc2Bucket(): Bucket {
            val bucket = Bucket.Builder.create(this, Names.s3BucketNameForEc2Files)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build()
            val s3Files = Source.asset("generated/s3/ec2")
            val deploySources = listOf(s3Files)
            val bucketDeployment = BucketDeployment.Builder.create(this, Names.s3BucketDeploymentNameForEc2Files)
                .sources(deploySources)
                .destinationBucket(bucket)
                .build()
            return bucket
        }

        private fun createEc2Instance(
            vpc: Vpc,
            securityGroup: SecurityGroup,
            bucket: Bucket,
            database: DatabaseInstance,
            databasePassword: Secret,
            subnetSelection: SubnetSelection
        ): Instance {
            val servicePrincipal = ServicePrincipal("ec2.amazonaws.com")
            val s3ReadOnlyAccess = ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess")
            val accessSecretsManager = ManagedPolicy.fromAwsManagedPolicyName("SecretsManagerReadWrite")
            val managedPolicyList = listOf(s3ReadOnlyAccess, accessSecretsManager)
            val role = Role.Builder.create(this, Names.roleName)
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
            val copySystemdEntry = InitSource.fromS3Object("/etc/systemd/system", bucket, "systemd.zip")
            val launchServer = InitCommand.argvCommand(listOf("systemctl", "start", "condorcet-backend"))
            val lines = listOf(
                "java -jar edit-json.jar configuration.json set string ${database.dbInstanceEndpointAddress} database host",
                "DATABASE_PASSWORD=\$(aws secretsmanager get-secret-value --region us-west-1 --output text --query SecretString --secret-id ${databasePassword.secretName})",
                "java -jar edit-json.jar secrets/secret-configuration.json set string \$DATABASE_PASSWORD database password"
            )
            val content = lines.joinToString("\n", "", "\n")
            val initializeContent = InitFile.fromString("/home/ec2-user/initialize.sh", content, executable)
            val chown =
                InitCommand.argvCommand(listOf("sudo", "chown", "-R", "ec2-user:ec2-user", "/home/ec2-user"))
            val initializeExec = InitCommand.shellCommand("./initialize.sh", userDir)
            val configElements = listOf(
                installJava,
                installMysql,
                copyJavaArchiveForServer,
                copySystemdEntry,
                initializeContent,
                initializeExec,
                chown,
                launchServer
            )
            val initConfig = InitConfig(configElements)
            val cloudFormationInit = CloudFormationInit.fromConfig(initConfig)
            val ec2 = Instance.Builder.create(this, Names.ec2InstanceId)
                .securityGroup(securityGroup)
                .vpc(vpc)
                .vpcSubnets(subnetSelection)
                .role(role)
                .instanceName(Names.ec2InstanceName)
                .instanceType(computeInstanceType)
                .machineImage(machineImage)
                .keyName(Names.keyName)
                .init(cloudFormationInit)
                .build()
            return ec2
        }

        private fun createApiGateway(ec2: Instance): IApi {
            val httpApi = HttpApi.Builder.create(this, Names.apiName).build()
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

        private fun createWebsiteBucket(ec2: Instance): Bucket {
            val bucket = Bucket.Builder.create(this, Names.s3BucketNameForWebsite)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .publicReadAccess(true)
                .build()
            val s3Files = Source.asset("generated/s3/website")
            val deploySources = listOf(s3Files)
            val bucketDeployment = BucketDeployment.Builder.create(this, Names.s3BucketDeploymentNameForWebsite)
                .sources(deploySources)
                .destinationBucket(bucket)
                .build()
            return bucket
        }

        private fun createCloudfrontDistribution(
            staticSiteBucket: Bucket,
            api: IApi
        ): Distribution {
            val staticSiteOrigin = S3Origin.Builder.create(staticSiteBucket).build()
            val staticSiteBehavior = BehaviorOptions.builder()
                .allowedMethods(AllowedMethods.ALLOW_ALL)
                .origin(staticSiteOrigin)
                .build()
            val distribution = Distribution.Builder.create(this, Names.distributionName)
                .defaultBehavior(staticSiteBehavior)
                .defaultRootObject("index.html")
                .build()
            return distribution
        }
    }

    override fun run() {
        val app = App()
        val vpcStack = VpcStack(app)
        val databaseStack = DatabaseStack(
            app,
            publicSubnets,
            vpcStack.vpc,
            vpcStack.securityGroup,
            vpcStack.databasePassword
        )
        val applicationStack = ApplicationStack(
            app,
            vpcStack.vpc,
            vpcStack.securityGroup,
            databaseStack.database,
            vpcStack.databasePassword,
            publicSubnets
        )
        app.synth()
    }
}
