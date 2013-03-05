package com.crawljax.core.configuration;

import org.apache.commons.configuration.PropertiesConfiguration;

/**
 * Reader for InputSpecification. For internal use only!
 * 
 * @author Danny
 * @version $Id: InputSpecificationReader.java 66 2010-01-13 14:27:36Z frankgroeneveld $
 */
public class InputSpecificationReader {

	private InputSpecification inputSpecification;

	/**
	 * Wrap around inputSpecification.
	 * 
	 * @param inputSpecification
	 *            The inputSpecification to wrap around.
	 */
	public InputSpecificationReader(InputSpecification inputSpecification) {
		this.inputSpecification = inputSpecification;
	}

	/**
	 * @return The configuration of the inputspecification.
	 */
	public PropertiesConfiguration getConfiguration() {
		return inputSpecification.getConfiguration();
	}

}
