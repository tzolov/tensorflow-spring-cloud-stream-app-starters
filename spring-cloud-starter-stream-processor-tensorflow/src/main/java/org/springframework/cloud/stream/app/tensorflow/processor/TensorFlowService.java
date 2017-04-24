package org.springframework.cloud.stream.app.tensorflow.processor;

import static org.apache.commons.io.IOUtils.buffer;
import static org.apache.commons.io.IOUtils.toByteArray;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

/**
 * Created by tzoloc on 4/24/17.
 */
@RefreshScope
@Service
public class TensorFlowService implements AutoCloseable {

	private static final Log logger = LogFactory.getLog(TensorflowProcessorConfiguration.class);

	@Autowired
	private TensorflowProcessorProperties properties;

	private Graph graph;

	@PostConstruct
	public void setUp() throws IOException {
		logger.info(">>>>>>>>>>>>>> CREATE ");
		try (InputStream is = properties.getModelLocation().getInputStream()) {
			graph = new Graph();
			logger.info("Loading TensorFlow graph model (" + properties.getModelLocation() + ") ... ");
			graph.importGraphDef(toByteArray(buffer(is)));
			logger.info("TensorFlow graph ready to serve.");
		}
	}

	public Tensor evaluate(Map<String, Object> feeds, String outputName, int outputIndex) {

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

	@Override
	public void close() throws Exception {
		logger.info(">>>>>>>>>>> Close TF Graph");
		if (graph != null) {
			graph.close();
		}
	}
}
