/**
 * Copyright (c) 2018-present, A2 Rešitve d.o.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package solutions.a2.kafka.config.aws;

import java.time.Duration;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * AWS Secrets Manager Provider for Kafka configuration
 *  
 * @author <a href="mailto:averemee@a2.solutions">Aleksei Veremeev</a>
 * 
 */
public class AwsSecretsManagerProviderConfig extends AbstractConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(AwsSecretsManagerProviderConfig.class);

	private final Region region;
	private final long secretTtlMs;

	public static ConfigDef config() {
		return new ConfigDef()
				.define("cloud.region", Type.STRING,
						Importance.HIGH,
						"The cloud region, for example - 'us-east-1'. Mandatory parameter.")
				.define("cloud.access.key", Type.STRING, "",
						Importance.LOW,
						"Credentials for accessing AWS services.\nUse only when EC2 IAM role, or ECS task role etc not suitable for you.")
				.define("cloud.access.secret", Type.PASSWORD, "",
						Importance.LOW,
						"Credentials secret for accessing AWS services.\nRequired only when 'cloud.access.key' is set.")
				.define("cloud.secret.ttl.ms", Type.LONG, Duration.ofDays(30).toMillis(),
						Importance.LOW,
						"The time interval in ms during which the secret is considered valid.\n" +
						"When this time expires the AWS Secret Manager is queried again, causing connector(s) to restart.\n" +
						"Default value - 30 days.")
				;
	}

	/**
	 * Provider configuration
	 * @param originals
	 * @throws KafkaException
	 */
	public AwsSecretsManagerProviderConfig(Map<?, ?> originals) throws KafkaException {
		super(config(), originals);

		//java.lang.IllegalArgumentException: region must not be blank or empty
		region = Region.of(this.getString("cloud.region"));
		if (region.metadata() == null) {
			final String wrongRegionMsg = String.format(
					"Invalid value '%s' specified for 'cloud.region parameter'!",
					this.getString("cloud.region"));
			LOGGER.error(wrongRegionMsg);
			throw new KafkaException(wrongRegionMsg);
		} else {
			LOGGER.info("AwsSecretsManagerProvider's region ='{}'", region);
		}
		secretTtlMs = this.getLong("cloud.secret.ttl.ms");
	}

	/**
	 * Returns AWS region
	 * @return
	 */
	public Region getRegion() {
		return region;
	}

	/**
	 * Returns secret TTL in ms
	 * @return
	 */
	public long getSecretTtlMs() {
		return secretTtlMs;
	}

	/**
	 * Returns AWS Secrets Manager Client
	 * @return
	 * @throws KafkaException
	 */
	public SecretsManagerClient getSecretsManagerClient() throws KafkaException {
		if (StringUtils.isBlank(this.getString("cloud.access.key"))) {
			LOGGER.debug("Credentials will be used from Instance/Task Role, environment, etc...");
			return
					SecretsManagerClient.builder()
						.region(region)
						.build();
		} else {
			LOGGER.debug(
					"Credentials for accces key '{}' will be used", this.getString("cloud.access.key"));
			AwsCredentialsProvider acp = null;
			try {
				acp = StaticCredentialsProvider.create(
						AwsBasicCredentials.create(
							this.getString("cloud.access.key"),
							this.getPassword("cloud.access.secret").value()));
			} catch (Exception e) {
				LOGGER.error("Unable to create static credentials: {}", e.getMessage());
				throw new KafkaException(e);
			}
			return
					SecretsManagerClient.builder()
						.credentialsProvider(acp)
						.region(region)
						.build();
		}
	}

	/**
	 * Returns AWS STS Client
	 * @return
	 * @throws KafkaException
	 */
	public StsClient getStsClient() throws KafkaException {
		if (StringUtils.isBlank(this.getString("cloud.access.key"))) {
			LOGGER.debug("Credentials will be used from Instance/Task Role, environment, etc...");
			return
					StsClient.builder()
						.region(region)
						.build();
		} else {
			LOGGER.debug(
					"Credentials for accces key '{}' will be used", this.getString("cloud.access.key"));
			AwsCredentialsProvider acp = null;
			try {
				acp = StaticCredentialsProvider.create(
						AwsBasicCredentials.create(
							this.getString("cloud.access.key"),
							this.getPassword("cloud.access.secret").value()));
			} catch (Exception e) {
				LOGGER.error("Unable to create static credentials: {}", e.getMessage());
				throw new KafkaException(e);
			}
			return
					StsClient.builder()
						.credentialsProvider(acp)
						.region(region)
						.build();
		}
	}

}
