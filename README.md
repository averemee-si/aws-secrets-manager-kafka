## Apache Kafka Configuration Provider for AWS Secrets Manager

## License

This project is licensed under the Apache-2.0 License.

## Introduction
Apache Kafka Configuration Provider for AWS able to retrieve secrets from [AWS Secrets Manager](https://aws.amazon.com/secrets-manager/).

## Building from source
After you've downloaded the code from GitHub, you can build it using Gradle. Use this command:
 
 `gradle clean build`
 
The generated jar files can be found at: `build/libs/`.

An uber jar containing the library and all its relocated dependencies except the kafka client can
 also be built. Use this command: 

`gradle clean shadowJar` 

The generated uber jar file can also be found at: `build/libs/`. At runtime, the uber jar expects to find the kafka
 client library on the classpath.
 
 ## Validating secure dependencies
To ensure no security vulnerabilities in the dependency libraries, run the following.

 `gradle dependencyCheckAnalyze`

If the above reports any vulnerabilities, upgrade dependencies to use the respective latest versions.

## Configuring the Apache Kafka Configuration Provider for AWS Secrets Manager

Apache Kafka Configuration Provider for AWS Secrets Manager finds IAM credentials using the [AWS Default Credentials Provider Chain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html). To overwrite you need to set optional parammeters `cloud.access.key` and `cloud.access.secret`


### cloud.region
Mandatory parameter, name of AWS region

### cloud.access.key
Credentials for accessing AWS services, use only when you need to overwrite credentials from [AWS Default Credentials Provider Chain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html)

### cloud.access.secret
Credentials secret for accessing AWS services. Required only when `cloud.access.key` is set

###cloud.secret.ttl.ms

The time interval in ms during which the secret is considered valid. Default value - **Duration.ofDays(30).toMillis()**.
Set the value according to the [key rotation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotating-secrets.html) schedule, when this time interval expires the [AWS Secret Manager](https://aws.amazon.com/secrets-manager/) is queried again, causing connector(s) to restart

## Using the Apache Kafka Configuration Provider for AWS Secrets Manager

1. Open the Secrets Manager console at [https://console.aws.amazon.com/secretsmanager/](https://console.aws.amazon.com/secretsmanager/)

2. Create a new secret to store your database sign-in credentials. For instructions, see [Create a secret](https://docs.aws.amazon.com/secretsmanager/latest/userguide/manage_create-basic-secret.html) in the [AWS Secrets Manager User Guide](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)

3. Create a custom worker configuration with information about Apache Kafka Configuration Provider for AWS Secrets Manager

```
config.providers=secretsManager
config.providers.secretsManager.class=solutions.a2.kafka.config.aws.AwsSecretsManagerProvider
config.providers.secretsManager.param.cloud.region=<AWS region>
```

4. Set for connector (example below for [Debezium](https://debezium.io/) credentials)

```
database.user=<${secretsManager:test/oracle/TESTDATA:username}>"
database.password=<${secretsManager:test/oracle/TESTDATA:password}>"
```

