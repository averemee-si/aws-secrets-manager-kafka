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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigData;
import org.apache.kafka.common.config.provider.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DecryptionFailureException;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * AWS Secrets Manager Provider for Kafka
 *  
 * @author <a href="mailto:averemee@a2.solutions">Aleksei Veremeev</a>
 * 
 */
public class AwsSecretsManagerProvider implements ConfigProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(AwsSecretsManagerProvider.class);

	private SecretsManagerClient smClient;
	private long secretTtlMs;

	@Override
	public ConfigData get(String path) {
		return get(path, Collections.emptySet());
	}

	@Override
	public ConfigData get(String path, Set<String> keys) {
		LOGGER.debug("path = {}, count of keys = {}", path, keys.size());
		GetSecretValueRequest request = GetSecretValueRequest.builder()
				.secretId(path)
				.build();
		try {
			GetSecretValueResponse response = smClient.getSecretValue(request);
			final Map<String, String> secrets = parseResponse(response);
			if (keys == null || keys.size() == 0) {
				return new ConfigData(secrets, secretTtlMs);
			} else {
				final Map<String, String> reduced = new HashMap<>();
				for (String key : keys) {
					final String value = secrets.get(key);
					if (value == null) {
						final String errMsg = String.format("Key entry '%s' is not found at path '%s'", key, path);
						LOGGER.error(errMsg);
						throw ResourceNotFoundException.builder()
								.message(errMsg)
								.build();
					} else {
						reduced.put(key, value);
					}
				}
				return new ConfigData(reduced, secretTtlMs);
			}
		} catch (ResourceNotFoundException rnfe) {
			LOGGER.error("Secret '{}' not found!", path);
			throw new KafkaException(rnfe);
		} catch (DecryptionFailureException dfe) {
			LOGGER.error("Unable to decrypt secret '{}'!\nPlease check KMS permissions", path);
			throw new KafkaException(dfe);
		} catch (AwsServiceException ase) {
			LOGGER.error("Service exception while querying for secret '{}'!", path);
			throw new KafkaException(ase);
		} catch (SdkClientException sce) {
			LOGGER.error("Client exception while querying for secret '{}'!", path);
			throw new KafkaException(sce);
		}
	}

	@Override
	public void configure(Map<String, ?> configs) {
		try {
			AwsSecretsManagerProviderConfig config = new AwsSecretsManagerProviderConfig(configs);
			smClient = config.getSecretsManagerClient();
			secretTtlMs = config.getSecretTtlMs();
			final GetCallerIdentityResponse caller = config.getStsClient().getCallerIdentity();
			LOGGER.info("AwsSecretsManagerProvider connected as {} to account {}",
					caller.arn(), caller.account());
		} catch (KafkaException ke) {
			LOGGER.error("Exception while configuring '{}'", AwsSecretsManagerProvider.class.getCanonicalName());
			final StringWriter sw = new StringWriter();
			final PrintWriter pw = new PrintWriter(sw);
			ke.printStackTrace(pw);
			LOGGER.error(sw.toString());
			throw ke;
		} catch (SdkException se) {
			LOGGER.error("Unable to get information about caller identity - {}", se.getMessage());
			final StringWriter sw = new StringWriter();
			final PrintWriter pw = new PrintWriter(sw);
			se.printStackTrace(pw);
			LOGGER.error(sw.toString());
		}
	}

	@Override
	public void close() throws IOException {
		if (smClient != null) {
			smClient.close();
		}
	}

	private static Map<String, String> parseResponse(GetSecretValueResponse response) {
		LOGGER.debug("Processing secret with ARN '{}' named '{}'",
				response.arn(), response.name());
		return parseResponse(response.secretString());
	}

	/**
	 * Returns AWS Secrets Manager response as two strings
	 * @param secretValues AWS Secrets Manager response in JSON format
	 * @return String pair: username and password
	 */
	public static Map<String, String> parseResponse(String secretValues) {
		Map<String, String> secretData = new HashMap<>();
		final String allValues = StringUtils.substringBefore(StringUtils.substringAfter(secretValues, "{"), "}");
		for (String pair : StringUtils.split(allValues, ",")) {
			LOGGER.debug("Processing credentials pair {}", pair);
			secretData.put(
					StringUtils.substringAfter(StringUtils.substringBefore(pair, "\":"), "\""),
					StringUtils.substringBefore(StringUtils.substringAfter(pair, ":\""), "\""));
		}
		return secretData;
	}

}
