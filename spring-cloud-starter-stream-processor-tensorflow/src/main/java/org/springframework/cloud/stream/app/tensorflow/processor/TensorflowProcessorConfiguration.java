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

package org.springframework.cloud.stream.app.tensorflow.processor;

import static org.apache.commons.io.IOUtils.buffer;
import static org.apache.commons.io.IOUtils.toByteArray;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;
import org.xml.sax.SAXException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.tuple.Tuple;

/**
 * A processor that evaluates a machine learning model stored in TensorFlow's ProtoBuf format.
 *
 * @author Christian Tzolov
 */
@EnableBinding(Processor.class)
@EnableConfigurationProperties(TensorflowProcessorProperties.class)
public class TensorflowProcessorConfiguration implements AutoCloseable {

	private static final Log logger = LogFactory.getLog(TensorflowProcessorConfiguration.class);

	public static final String TF_OUTPUT_HEADER = "TF_OUTPUT";

	public static final String TF_INPUT_HEADER = "TF_INPUT";

	@Autowired
	private TensorflowProcessorProperties properties;

	@Autowired
	@Qualifier("tensorflowInputConverter")
	private TensorflowInputConverter tensorflowInputConverter;

	@Autowired
	@Qualifier("tensorflowOutputConverter")
	private TensorflowOutputConverter tensorflowOutputConverter;

	private Graph graph;

	@PostConstruct
	public void setUp() throws IOException, SAXException, JAXBException {
		try (InputStream is = properties.getModelLocation().getInputStream()) {
			graph = new Graph();
			logger.info("Loading TensorFlow graph model (" + properties.getModelLocation() + ") ... ");
			graph.importGraphDef(toByteArray(buffer(is)));
			logger.info("TensorFlow graph ready to serve.");
		}
	}

	@ServiceActivator(inputChannel = Processor.INPUT, outputChannel = Processor.OUTPUT)
	public Message<?> evaluate(Message<?> input) {

		Map<String, Object> processorContext = new ConcurrentHashMap<>();

		Map<String, Object> inputData = tensorflowInputConverter.convert(input, processorContext);

		Tensor outputTensor = this.evaluate(inputData, properties.getOutputName(), properties.getOutputIndex());

		Object outputData = tensorflowOutputConverter.convert(outputTensor, processorContext);

		if (properties.isSaveOutputInHeader()) {
			// Add the result to the message header
			return MessageBuilder
					.withPayload(input.getPayload())
					.copyHeadersIfAbsent(input.getHeaders())
					.setHeaderIfAbsent(TF_OUTPUT_HEADER, outputData)
					.build();
		}

		// Add the outputData as part of the message payload
		Message<?> outputMessage = MessageBuilder
				.withPayload(outputData)
				.copyHeadersIfAbsent(input.getHeaders())
				.build();

		return outputMessage;
	}

	private Tensor evaluate(Map<String, Object> feeds, String outputName, int outputIndex) {

		try (Session session = new Session(graph)) {
			Runner runner = session.runner();
			Tensor[] feedTensors = new Tensor[feeds.size()];
			try {
				int i = 0;
				for (Entry<String, Object> e : feeds.entrySet()) {
					Tensor tensor = Tensor.create(e.getValue());
					runner = runner.feed(e.getKey(), tensor);
					feedTensors[i++] = tensor;
				}
				return runner.fetch(outputName).run().get(outputIndex);
			}
			finally {
				if (feedTensors != null) {
					for (Tensor tensor : feedTensors) {
						if (tensor != null) {
							tensor.close();
						}
					}
				}
			}
		}
	}

	@Bean
	@ConditionalOnMissingBean(name = "tensorflowOutputConverter")
	public TensorflowOutputConverter tensorflowOutputConverter() {
		// Default implementations serializes the Tensor into Tuple
		return new TensorflowOutputConverter<Tuple>() {
			@Override
			public Tuple convert(Tensor tensor, Map<String, Object> processorContext) {
				return TensorTupleConverter.toTuple(tensor);
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean(name = "tensorflowInputConverter")
	public TensorflowInputConverter tensorflowInputConverter() {
		return new TensorflowInputConverter() {

			@Override
			public Map<String, Object> convert(Message<?> input, Map<String, Object> processorContext) {

				if (input.getHeaders().containsKey(TF_INPUT_HEADER)) {
					return (Map<String, Object>) input.getHeaders().get(TF_INPUT_HEADER, Map.class);
				}
				else if (input.getPayload() instanceof Map) {
					return (Map<String, Object>) input.getPayload();
				}
				throw new RuntimeException("Unsupported input format: " + input);

			}
		};
	}

	@Override
	public void close() throws Exception {
		graph.close();
	}
}
