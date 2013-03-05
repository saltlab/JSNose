package com.crawljax.core;

import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.core.plugin.CrawljaxPluginsUtil;
import com.crawljax.core.state.Eventable;
import com.crawljax.core.state.StateFlowGraph;
import com.crawljax.core.state.StateMachine;
import com.crawljax.core.state.StateVertix;
import com.crawljax.plugins.aji.JSModifyProxyPlugin;

/**
 * This is the initial Crawler. An initial crawler crawls only the index page, creates the index
 * state and builds a session object and resumes the normal operations.
 * 
 * @author Stefan Lenselink <S.R.Lenselink@student.tudelft.nl>
 * @version $Id: InitialCrawler.java 483 2011-03-29 01:16:32Z amesbah $
 */

public class InitialCrawler extends Crawler {

	private static final Logger LOGGER = Logger.getLogger(InitialCrawler.class);

	private final CrawljaxController controller;

	private EmbeddedBrowser browser; // should be final but try-catch prevents...

	private StateMachine stateMachine;

	/**
	 * The default constructor.
	 * 
	 * @param mother
	 *            the controller to use.
	 */
	public InitialCrawler(CrawljaxController mother) {
		super(mother, new ArrayList<Eventable>(), "initial");
		controller = mother;
	}

	@Override
	public EmbeddedBrowser getBrowser() {
		return browser;
	}

	@Override
	public StateMachine getStateMachine() {
		return stateMachine;
	}

	@Override
	public void run() {

		try {
			browser = controller.getBrowserPool().requestBrowser();
		} catch (InterruptedException e) {
			LOGGER.error("The request for a browser was interuped.");
		}

		goToInitialURL();

		/**
		 * Build the index state
		 */
		StateVertix indexState =
		        new StateVertix(this.getBrowser().getCurrentUrl(), "index", this.getBrowser()
		                .getDom(), controller.getStrippedDom(this.getBrowser()),null);

		/**
		 * Build the StateFlowGraph
		 */
		StateFlowGraph stateFlowGraph = new StateFlowGraph(indexState);

		/**
		 * Build the StateMachine
		 */
		stateMachine =
		        new StateMachine(stateFlowGraph, indexState, controller.getInvariantList());

		/**
		 * Build the CrawlSession
		 */
		CrawlSession session =
		        new CrawlSession(controller.getBrowserPool(), stateFlowGraph, indexState,
		                controller.getStartCrawl(), controller.getConfigurationReader());
		controller.setSession(session);
		
		
		
		// Amin: setDiverseCrawling and setEfficientCrawling
		stateFlowGraph.setDiverseCrawling(controller.isDiverseCrawling());
		stateFlowGraph.setEfficientCrawling(controller.isEfficientCrawling());

		
		boolean getCoverage = false;

		if (getCoverage){
			// Amin: Calculate initial code coverage
			for (String modifiedJS : JSModifyProxyPlugin.getModifiedJSList()){
				// LOGGER.info("** MODIFIEDS ARE: " + modifiedJS);
				try{
					Object counter =  this.browser.executeJavaScript("return " + modifiedJS + "_counter;");
					ArrayList countList = (ArrayList) counter;

					this.controller.setCountList(modifiedJS, counter);
				}catch (Exception e) {
					LOGGER.info("Could not execute script");
				}
			}
			double cov = this.controller.getCoverage();

			if (controller.isDiverseCrawling())
				controller.getSession().getStateFlowGraph().setInitialCoverage(indexState, cov);

		}



		/**
		 * Run OnNewState Plugins for the index state.
		 */
		CrawljaxPluginsUtil.runOnNewStatePlugins(session);

		/**
		 * The initial work is done, continue with the normal procedure!
		 */
		super.run();

	}
}
