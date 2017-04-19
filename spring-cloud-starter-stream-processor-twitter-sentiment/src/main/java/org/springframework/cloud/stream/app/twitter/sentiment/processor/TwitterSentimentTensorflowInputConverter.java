/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.twitter.sentiment.processor;

import static org.springframework.util.StringUtils.isEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.stream.app.tensorflow.processor.TensorflowInputConverter;
import org.springframework.core.io.Resource;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Christian Tzolov
 */
public class TwitterSentimentTensorflowInputConverter implements TensorflowInputConverter {

	private static final Log logger = LogFactory.getLog(TwitterSentimentTensorflowInputConverter.class);

	public static final Float DROPOUT_KEEP_PROB_VALUE = new Float(1.0);

	public static final String DATA_IN = "data_in";

	public static final String DROPOUT_KEEP_PROB = "dropout_keep_prob";

	public static final String TWEET_TEXT_TAG = "text";

	public static final String TWEET_ID_TAG = "id";

	private final WordVocabulary wordVocabulary;

	private final ObjectMapper jsonConverter;

	public TwitterSentimentTensorflowInputConverter(Resource vocabularLocation) {
		try (InputStream is = vocabularLocation.getInputStream()) {
			wordVocabulary = new WordVocabulary(is);
			jsonConverter = new ObjectMapper();
			Assert.notNull(wordVocabulary, "Failed to initialize the word vocabulary");
			Assert.notNull(jsonConverter, "Failed to initialize the jsonConverter");
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to initialize the Vocabulary", e);
		}

		logger.info("Word Vocabulary Initialized");
	}

	@Override
	public Map<String, Object> convert(Message<?> input) {

		try {
			Object payload = input.getPayload();
			if (payload instanceof String) {
				return getStringObjectMap(jsonConverter.readValue((String) payload, Map.class));
			}
			else if (payload instanceof Map) {
				return getStringObjectMap((Map) payload);
			}

			throw new IllegalArgumentException("Unsupported payload type:" + input.getPayload());
		}
		catch (IOException e) {
			throw new RuntimeException("Can't parse input tweet json: " + input.getPayload());
		}

	}

	private Map<String, Object> getStringObjectMap(Map jsonMap) {
		Assert.notNull(jsonMap, "Failed to parse the Tweet json!");

		String tweetText = (String) jsonMap.get(TWEET_TEXT_TAG);

		if (isEmpty(tweetText)) {
			logger.warn("Tweet with out text: " + jsonMap.get(TWEET_ID_TAG));
			tweetText = "";
		}

		int[][] tweetVector = wordVocabulary.vectorizeSentence(tweetText);

		Assert.notEmpty(tweetVector, "Failed to vectorize the tweet text: " + tweetText);

		Map<String, Object> response = new HashMap<>();
		response.put(DATA_IN, tweetVector);
		response.put(DROPOUT_KEEP_PROB, DROPOUT_KEEP_PROB_VALUE);

		return response;
	}
}
