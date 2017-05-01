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

package org.springframework.cloud.stream.app.label.image.processor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tensorflow.Tensor;

import org.springframework.cloud.stream.app.tensorflow.processor.TensorflowOutputConverter;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Floats;

/**
 * @author Christian Tzolov
 */
public class LabelImageTensorflowOutputConverter implements TensorflowOutputConverter<String> {

	private static final Log logger = LogFactory.getLog(LabelImageTensorflowOutputConverter.class);

	private final List<String> labels;

	private final ObjectMapper objectMapper;

	private final int alternativesLength;

	public LabelImageTensorflowOutputConverter(Resource labelsLocation, int alternativesLength) {
		this.alternativesLength = alternativesLength;
		try (InputStream is = labelsLocation.getInputStream()) {
			labels = IOUtils.readLines(is, Charset.forName("UTF-8"));
			objectMapper = new ObjectMapper();
			Assert.notNull(labels, "Failed to initialize the labels list");
			Assert.notNull(objectMapper, "Failed to initialize the objectMapper");
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to initialize the Vocabulary", e);
		}

		logger.info("Word Vocabulary Initialized");

	}

	@Override
	public String convert(Tensor tensor, Map<String, Object> processorContext) {
		final long[] rshape = tensor.shape();
		if (tensor.numDimensions() != 2 || rshape[0] != 1) {
			throw new RuntimeException(
					String.format(
							"Expected model to produce a [1 N] shaped tensor where N is the number of labels, instead it produced one with shape %s",
							Arrays.toString(rshape)));
		}
		int nlabels = (int) rshape[1];
		float[] labelProbabilities = tensor.copyTo(new float[1][nlabels])[0];

		int mostProbableLabelIndex = maxProbabilityIndex(labelProbabilities);

		Map<String, Object> outputJsonMap = new HashMap<>();
		outputJsonMap.put("label", labels.get(mostProbableLabelIndex));


		if (alternativesLength > 0) {
			List<Integer> top10Probabilities = topKProbabilities(labelProbabilities, alternativesLength);

			Map[] alternatives = new Map[top10Probabilities.size()];
			for (int i = 0; i < top10Probabilities.size(); i++) {
				int probabilityInidex = top10Probabilities.get(i);
				alternatives[i] = toMap(labels.get(probabilityInidex), labelProbabilities[probabilityInidex]);
			}
			outputJsonMap.put("alternatives", alternatives);
		}

		try {
			return objectMapper.writeValueAsString(outputJsonMap);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to generate JSON output", e);
		}
	}

	private List<Integer> topKProbabilities(final float[] labelProbabilities, int k) {

		List<Integer> list = new ArrayList<>(labelProbabilities.length);
		for (int i = 0; i < labelProbabilities.length; i++) {
			list.add(i);
		}

		List<Integer> topK = new Ordering<Integer>() {
			@Override
			public int compare(Integer left, Integer right) {
				return Floats.compare(labelProbabilities[left], labelProbabilities[right]);
			}
		}.greatestOf(list, k);

		return topK;
	}

	private Map toMap(String key, float value) {
		Map<String, Float> map = new HashMap<>();
		map.put(key, value);
		return map;
	}

	private int maxProbabilityIndex(float[] probabilities) {
		int best = 0;
		for (int i = 1; i < probabilities.length; ++i) {
			if (probabilities[i] > probabilities[best]) {
				best = i;
			}
		}
		return best;
	}
}
