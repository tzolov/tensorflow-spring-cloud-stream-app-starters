/*
 * Copyright 2015-2017 the original author or authors.
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

import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties("inception")
@Validated
public class LabelImageProcessorProperties {

	private Resource labelsLocation;

	/**
	 * Number of top K alternatives to add to the result. Only used when the alternativesLength > 0.
	 */
	private int alternativesLength = -1;

	@NotNull
	public Resource getLabelsLocation() {
		return labelsLocation;
	}

	public void setLabelsLocation(Resource labelsLocation) {
		this.labelsLocation = labelsLocation;
	}

	public int getAlternativesLength() {
		return alternativesLength;
	}

	public void setAlternativesLength(int alternativesLength) {
		this.alternativesLength = alternativesLength;
	}
}
