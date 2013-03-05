package com.crawljax.core;

import java.text.DecimalFormat;

import org.apache.commons.configuration.ConfigurationException;

import com.crawljax.browser.EmbeddedBrowser.BrowserType;
import com.crawljax.condition.NotXPathCondition;
import com.crawljax.core.configuration.CrawlSpecification;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.configuration.Form;
import com.crawljax.core.configuration.InputSpecification;
import com.crawljax.core.configuration.ThreadConfiguration;
import com.crawljax.core.state.Eventable;
import com.crawljax.core.state.StateFlowGraph;

/**
 * This is an state coverage estimator for Crawljax using Monte Carlo method
 * 
 * TODO: should use more diverse sampling for better approximation
 * 
 * @author aminmf@ece.ubc.ca (Amin Milani Fard)
 * @version $id$
 */
public final class CrawljaxEstimator {

	// Crawljax FSM values
	private int numExpState; 		// number of already explored (unique) states
	private int numSampledEvents; 	// number of sampled events (fired clickables)
	private int numUnsampledEvents; // number of unsampled events (candidate clickables) before the sampling
	
	// Actual values
	private int actNumTotalSpace; 		// Actual number of total states (it is known for a given application)
	private int actNumTotalEvents; 		// Actual number of total events (it is known for a given application)
	private int actNumUnexpState; 		// Actual number of unexplored states (= actNumTotalSpace - numExpState)
	private double actCoverage; 		// Actual state-space coverage
	
	// Estimated values
	private int estNumUnexpState; 	// Estimated number of unexplored states
	private int estNumTotalSpace; 	// Estimated number of total states  (= numExpState + estNumUnexpState)
	private double estCoverage; 	// Estimated state-space coverage

	
	/**
	 * The constructor method gets the total actual state-space size.
	 * It uses this value to estimate the space coverage
	 * 
	 * @param totalSpaceSize
	 *            the total actual state-space size
	 */
	public CrawljaxEstimator(int totalSpaceSize){
		actNumTotalSpace = totalSpaceSize;
	}


	public void setActualValues(int totalSpaceSize){
		actNumTotalSpace = totalSpaceSize;
	}

	
	/**
	 * Updating estimator values and estimating coverage
	 * 
	 * @param stateFlowGraph
	 *            the current state flow graph
	 */
	public void updateEstimator(int expState, int sampledEvents){
		numExpState = expState;
		numSampledEvents = sampledEvents;
		
		actNumUnexpState = actNumTotalSpace - numExpState;
		numUnsampledEvents = CrawljaxController.NumCandidateClickables;
		
		if (numSampledEvents!=0)
			estNumUnexpState = (int)(((double)(numExpState-1) / (double)numSampledEvents) * numUnsampledEvents);
		else
			estNumUnexpState = 0;
		
		estNumTotalSpace = numExpState + estNumUnexpState;
		
		if (estNumTotalSpace!=0)
			estCoverage = (double)numExpState / (double)estNumTotalSpace;
		actCoverage = (double)numExpState / (double)actNumTotalSpace;
	}

	
	/**
	 * Returns the estimated state coverage
	 * 
	 * @return estimated state coverage
	 */
	public double getEstimateCoverage(){
		return estCoverage;
	}

	/**
	 * Returns the actual state coverage
	 * 
	 * @return actual state coverage
	 */
	public double getActualCoverage(){
		return actCoverage;
	}

	/**
	 * Create a string from the values in estimator (i.e., numExpState, estNumUnexpState, coverage, etc)
	 * 
	 * @return actual state coverage
	 */
	public String toString() {
		//String stat ="numExpState:" + numExpState + 
		//" numSampledEvents:" + numSampledEvents + " numUnsampledEvents: " + numUnsampledEvents +
		//" estNumUnexpState: " + estNumUnexpState + " estNumTotalSpace:" + estNumTotalSpace +		
		//" actCoverage:" + (float)actCoverage*100 + "% estCoverage:" + (float)estCoverage*100 +"%";
		DecimalFormat df = new DecimalFormat("##.##");
		//String stat = df.format( (float)estCoverage*100 ) +"%\t" + df.format( (float)actCoverage*100 )+ "%\t";
		String stat = df.format( (float)estCoverage*100 ) +"\t" + df.format( (float)actCoverage*100 );
		
		return stat;
	}
 		
}
