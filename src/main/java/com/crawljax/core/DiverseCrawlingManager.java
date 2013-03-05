package com.crawljax.core;

import org.apache.log4j.Logger;

/**
 * Class that manages crawler threads. A thread from this class is created by CrawljaxController.
 * 
 * @author Amin Milani Fard <aminmf@ece.ubc.ca>
 */
public class DiverseCrawlingManager implements Runnable {

	/** 
	 * this is to check when this thread should finish its working. set to true by CrawljaxController
	 */
	private boolean finishedCrawling = false;
	
	private static final Logger LOGGER = Logger.getLogger(DiverseCrawlingManager.class.getName());

	private final CrawljaxController controller;

	private String name = "DiverseCrawlingManager";

	/**
	 * Public constructor
	 * 
	 * @param mother
	 *            the main CrawljaxController
	 */
	public DiverseCrawlingManager(CrawljaxController mother) {
		this.controller = mother;
	}
	
	// controller sets finishedCrawling to true when all crawlers finished crawling 
	void finishedWorking(){
		finishedCrawling = true;
	}
	
	/**
	 * TODO: Description
	 */
	public void run() {
		while (!finishedCrawling){
			/**
			 * Check if all crawler threads are waiting
			 */
			synchronized(this){
				if (controller.allCrawlersWaiting()){
					LOGGER.info("All crawler threads are waiting now!");
					//  PD_weight and DD_weight should be set for notifyWaitingCrawlers
					controller.notifyWaitingCrawlers(1.0, 1.0);
					LOGGER.info("YES!!!!");
				}
			}
		}
		LOGGER.info("Crawling is finished. Shutdown diverse crawling manager...");
	}

	public String toString() {
		return this.name;
	}
}