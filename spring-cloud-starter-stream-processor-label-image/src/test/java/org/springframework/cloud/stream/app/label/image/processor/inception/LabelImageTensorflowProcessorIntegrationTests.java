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

package org.springframework.cloud.stream.app.label.image.processor.inception;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.cloud.stream.app.tensorflow.processor.TensorflowProcessorConfiguration.TF_OUTPUT_HEADER;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.app.label.image.processor.LabelImageProcessorConfiguration;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Christian Tzolov
 */
@SuppressWarnings("SpringJavaAutowiringInspection")
@RunWith(SpringRunner.class)
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"tensorflow.modelLocation=http://dl.bintray.com/big-data/generic/tensorflow_inception_graph.pb",
				"tensorflow.outputName=output",
				"inception.labelsLocation=http://dl.bintray.com/big-data/generic/imagenet_comp_graph_label_strings.txt"
		})
@DirtiesContext
public abstract class LabelImageTensorflowProcessorIntegrationTests {

	@Autowired
	protected Processor channels;

	@Autowired
	protected MessageCollector messageCollector;


	@TestPropertySource(properties = {"tensorflow.saveOutputInHeader=true"})
	public static class OutputInHeaderTests extends LabelImageTensorflowProcessorIntegrationTests {

		@Test
		public void testEvaluationPositive() throws IOException {
			try(InputStream is = new ClassPathResource("/images/panda.jpeg").getInputStream()) {

				byte[] image = IOUtils.toByteArray(is);

				testEvaluationWithOutputInHeader(
						image, "{\"label\":\"giant panda\"}");
			}
		}

		private void testEvaluationWithOutputInHeader(byte[] image, String resultJson) {
			channels.input().send(MessageBuilder.withPayload(image).build());

			Message<?> received = messageCollector.forChannel(channels.output()).poll();

			Assert.assertThat(received.getHeaders().get(TF_OUTPUT_HEADER).toString(), equalTo(resultJson));
		}
	}

	@TestPropertySource(properties = {"tensorflow.saveOutputInHeader=false"})
	public static class OutputInPayloadTests extends LabelImageTensorflowProcessorIntegrationTests {

		@Test
		public void testEvaluationPositive() throws IOException {
			try(InputStream is = new ClassPathResource("/images/panda.jpeg").getInputStream()) {

				byte[] image = IOUtils.toByteArray(is);

				channels.input().send(MessageBuilder.withPayload(image).build());

				Message<String> received = (Message<String>) messageCollector.forChannel(channels.output()).poll();

				Assert.assertTrue(received.getPayload().getClass().isAssignableFrom(String.class));

				Assert.assertThat(received.getPayload().toString(),
						equalTo("{\"label\":\"giant panda\"}"));
			}
		}
	}

	@TestPropertySource(properties = {"inception.alternativesLength=3"})
	public static class OutputWithAlternativesTests extends LabelImageTensorflowProcessorIntegrationTests {

		@Test
		public void testEvaluationPositive() throws IOException {
			try(InputStream is = new ClassPathResource("/images/panda.jpeg").getInputStream()) {

				byte[] image = IOUtils.toByteArray(is);

				channels.input().send(MessageBuilder.withPayload(image).build());

				Message<String> received = (Message<String>) messageCollector.forChannel(channels.output()).poll();

				Assert.assertTrue(received.getPayload().getClass().isAssignableFrom(String.class));

				Assert.assertThat(received.getPayload().toString(),
						equalTo("{\"alternatives\":[" +
								"{\"giant panda\":0.98649305}," +
								"{\"badger\":0.010562794}," +
								"{\"ice bear\":0.001130851}]," +
								"\"label\":\"giant panda\"}"));
			}
		}
	}

	@SpringBootApplication
	@Import(LabelImageProcessorConfiguration.class)
	public static class TensorflowProcessorApplication {

	}
}
