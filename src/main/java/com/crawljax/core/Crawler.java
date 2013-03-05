package com.crawljax.core;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.core.configuration.CrawljaxConfigurationReader;
import com.crawljax.core.exception.BrowserConnectionException;
import com.crawljax.core.exception.CrawlPathToException;
import com.crawljax.core.plugin.CrawljaxPluginsUtil;
import com.crawljax.core.state.CrawlPath;
import com.crawljax.core.state.Eventable;
import com.crawljax.core.state.Eventable.EventType;
import com.crawljax.core.state.Identification;
import com.crawljax.core.state.StateFlowGraph;
import com.crawljax.core.state.StateMachine;
import com.crawljax.core.state.StateVertix;
import com.crawljax.forms.FormHandler;
import com.crawljax.forms.FormInput;
import com.crawljax.plugins.aji.JSModifyProxyPlugin;
import com.crawljax.util.ElementResolver;

/**
 * Class that performs crawl actions. It is designed to be run inside a Thread.
 *
 * @see #run()
 * @author dannyroest@gmail.com (Danny Roest)
 * @author Stefan Lenselink <S.R.Lenselink@student.tudelft.nl>
 * @author Amin Milani Fard
 * @version $Id: Crawler.java 492M 2012-05-29 19:12:22Z (local) $
 */
public class Crawler implements Runnable {

	/**
	 * Added by Amin
	 * CrawlStrategy: different crawling strategies set in the guidedCrawl()
	 * pauseFlag: used for wait/resume in diverse crawling
	 * strategicCrawl: if true, runs guidedCrawl which uses different CrawlStrategy. This is false by default.
	 */
	// Amin: Different strategies for the guided crawling
	enum CrawlStrategy {
		DFS, BFS, Rand, Div
	}
	private boolean pauseFlag = true;
	private boolean strategicCrawl = false;


	private static final Logger LOGGER = Logger.getLogger(Crawler.class.getName());

	private static final int ONE_SECOND = 1000;

	/**
	 * The main browser window 1 to 1 relation; Every Thread will get on browser assigned in the run
	 * function.
	 */
	private EmbeddedBrowser browser;

	/**
	 * The central DataController. This is a multiple to 1 relation Every Thread shares an instance
	 * of the same controller! All operations / fields used in the controller should be checked for
	 * thread safety.
	 */
	private final CrawljaxController controller;

	/**
	 * Depth register.
	 */
	private int depth = 0;

	/**
	 * The path followed from the index to the current state.
	 */
	private final CrawlPath backTrackPath;

	/**
	 * The utility which is used to extract the candidate clickables.
	 */
	private CandidateElementExtractor candidateExtractor;

	private boolean fired = false;

	/**
	 * The name of this Crawler when not default (automatic) this will be added to the Thread name
	 * in the thread as (name). In the {@link CrawlerExecutor#beforeExecute(Thread, Runnable)} the
	 * name is retrieved using the {@link #toString()} function.
	 *
	 * @see Crawler#toString()
	 * @see CrawlerExecutor#beforeExecute(Thread, Runnable)
	 */
	private String name = "";

	/**
	 * The sateMachine for this Crawler, keeping track of the path crawled by this Crawler.
	 */
	private final StateMachine stateMachine;

	private final CrawljaxConfigurationReader configurationReader;

	private FormHandler formHandler;

	/**
	 * The object to places calls to add new Crawlers or to remove one.
	 */
	private final CrawlQueueManager crawlQueueManager;

	/**
	 * Enum for describing what has happened after a {@link Crawler#clickTag(Eventable)} has been
	 * performed.
	 *
	 * @see Crawler#clickTag(Eventable)
	 */
	private enum ClickResult {
		cloneDetected, newState, domUnChanged
	}



	/**
	 * @param mother
	 *            the main CrawljaxController
	 * @param exactEventPath
	 *            the event path up till this moment.
	 * @param name
	 *            a name for this crawler (default is empty).
	 */
	public Crawler(CrawljaxController mother, List<Eventable> exactEventPath, String name) {
		this(mother, new CrawlPath(exactEventPath));
		this.name = name;
	}

	/**
	 * Private Crawler constructor for a 'reload' crawler. Only used internally.
	 *
	 * @param mother
	 *            the main CrawljaxController
	 * @param returnPath
	 *            the path used to return to the last state, this can be a empty list
	 * @deprecated better to use {@link #Crawler(CrawljaxController, CrawlPath)}
	 */
	@Deprecated
	protected Crawler(CrawljaxController mother, List<Eventable> returnPath) {
		this(mother, new CrawlPath(returnPath));
	}

	/**
	 * Private Crawler constructor for a 'reload' crawler. Only used internally.
	 *
	 * @param mother
	 *            the main CrawljaxController
	 * @param returnPath
	 *            the path used to return to the last state, this can be a empty list
	 */
	protected Crawler(CrawljaxController mother, CrawlPath returnPath) {
		this.backTrackPath = returnPath;
		this.controller = mother;
		this.configurationReader = controller.getConfigurationReader();
		this.crawlQueueManager = mother.getCrawlQueueManager();
		if (controller.getSession() != null) {
			this.stateMachine =
			        new StateMachine(controller.getSession().getStateFlowGraph(), controller
			                .getSession().getInitialState(), controller.getInvariantList());
			stateMachine.setEfficientCrawling(controller.isEfficientCrawling());
		} else {
			/**
			 * Reset the state machine to null, because there is no session where to load the
			 * stateFlowGraph from.
			 */
			this.stateMachine = null;
		}
	}

	/**
	 * Brings the browser to the initial state.
	 */
	public void goToInitialURL() {
		LOGGER.info("Loading Page "
		        + configurationReader.getCrawlSpecificationReader().getSiteUrl());
		getBrowser().goToUrl(configurationReader.getCrawlSpecificationReader().getSiteUrl());
		/**
		 * Thread safe
		 */
		controller.doBrowserWait(getBrowser());
		CrawljaxPluginsUtil.runOnUrlLoadPlugins(getBrowser());
	}

	/**
	 * Try to fire a given event on the Browser.
	 *
	 * @param eventable
	 *            the eventable to fire
	 * @return true iff the event is fired
	 */
	private boolean fireEvent(Eventable eventable) {
		if (eventable.getIdentification().getHow().toString().equals("xpath")
		        && eventable.getRelatedFrame().equals("")) {

			/**
			 * The path in the page to the 'clickable' (link, div, span, etc)
			 */
			String xpath = eventable.getIdentification().getValue();

			/**
			 * The type of event to execute on the 'clickable' like onClick, mouseOver, hover, etc
			 */
			EventType eventType = eventable.getEventType();

			/**
			 * Try to find a 'better' / 'quicker' xpath
			 */
			String newXPath = new ElementResolver(eventable, getBrowser()).resolve();
			if (newXPath != null && !xpath.equals(newXPath)) {
				LOGGER.info("XPath changed from " + xpath + " to " + newXPath + " relatedFrame:"
				        + eventable.getRelatedFrame());
				eventable =
				        new Eventable(new Identification(Identification.How.xpath, newXPath),
				                eventType);
			}
		}

		if (getBrowser().fireEvent(eventable)) {

			/**
			 * Let the controller execute its specified wait operation on the browser thread safe.
			 */
			controller.doBrowserWait(getBrowser());

			/**
			 * Close opened windows
			 */

			getBrowser().closeOtherWindows();

			return true; // A event fired
		} else {
			/**
			 * Execute the OnFireEventFailedPlugins with the current crawlPath with the crawlPath
			 * removed 1 state to represent the path TO here.
			 */
			CrawljaxPluginsUtil.runOnFireEventFailedPlugins(eventable, controller.getSession()
			        .getCurrentCrawlPath().immutableCopy(true));
			return false; // no event fired
		}
	}

	/**
	 * Enters the form data. First, the related input elements (if any) to the eventable are filled
	 * in and then it tries to fill in the remaining input elements.
	 *
	 * @param eventable
	 *            the eventable element.
	 */
	private void handleInputElements(Eventable eventable) {
		List<FormInput> formInputs = eventable.getRelatedFormInputs();

		for (FormInput formInput : formHandler.getFormInputs()) {
			if (!formInputs.contains(formInput)) {
				formInputs.add(formInput);
			}
		}
		eventable.setRelatedFormInputs(formInputs);
		formHandler.handleFormElements(formInputs);
	}

	/**
	 * Reload the browser following the {@link #backTrackPath} to the given currentEvent.
	 *
	 * @throws CrawljaxException
	 *             if the {@link Eventable#getTargetStateVertix()} encounters an error.
	 */
	private void goBackExact() throws CrawljaxException {
		/**
		 * Thread safe
		 */
		StateVertix curState = controller.getSession().getInitialState();

		for (Eventable clickable : backTrackPath) {

			if (!controller.getElementChecker().checkCrawlCondition(getBrowser())) {
				return;
			}

			LOGGER.info("Backtracking by executing " + clickable.getEventType() + " on element: "
			        + clickable);

			this.getStateMachine().changeState(clickable.getTargetStateVertix());

			curState = clickable.getTargetStateVertix();

			controller.getSession().addEventableToCrawlPath(clickable);

			this.handleInputElements(clickable);

			if (this.fireEvent(clickable)) {

				// TODO ali, do not increase depth if eventable is from guidedcrawling
				depth++;

				/**
				 * Run the onRevisitStateValidator(s)
				 */
				CrawljaxPluginsUtil.runOnRevisitStatePlugins(this.controller.getSession(),
				        curState);
			}

			if (!controller.getElementChecker().checkCrawlCondition(getBrowser())) {
				return;
			}
		}
	}

	/**
	 * @param eventable
	 *            the element to execute an action on.
	 * @return the result of the click operation
	 * @throws CrawljaxException
	 *             an exception.
	 */
	private ClickResult clickTag(final Eventable eventable) throws CrawljaxException {
		// load input element values
		this.handleInputElements(eventable);

		LOGGER.info("Executing " + eventable.getEventType() + " on element: " + eventable
		        + "; State: " + this.getStateMachine().getCurrentState().getName());


		// Added by Amin: reducing from CandidateElements of the current state
		this.getStateMachine().getCurrentState().decreaseCandidateElements();
		// check if all candidateElements have been fired on the current state
		if (this.getStateMachine().getCurrentState().isFullyExpanded())
			this.controller.getSession().getStateFlowGraph().removeFromNotFullExpandedStates(this.getStateMachine().getCurrentState());



		// Amin: state-space estimation
		//String est = this.controller.getSession().getStateFlowGraph().updateEstimator();
		//this.controller.writeEstimationToFile( est ); // est : estimated and actual state-space coverage 
		CrawljaxController.NumCandidateClickables--;


		if (this.fireEvent(eventable)) {
			StateVertix newState =
				new StateVertix(getBrowser().getCurrentUrl(), controller.getSession()
						.getStateFlowGraph().getNewStateName(), getBrowser().getDom(),
						this.controller.getStrippedDom(getBrowser()), backTrackPath);


			boolean getCoverage = false;
			
			if (getCoverage){
				//Amin: calculate code coverage
				for (String modifiedJS : JSModifyProxyPlugin.getModifiedJSList()){
					//System.out.println("MODIFIED CODES ARE: " + modifiedJS);
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
					controller.getSession().getStateFlowGraph().setLatestCoverage(cov);
			}


			boolean domMutationNotifierPluginCheck = this.controller.getDomMutationNotifierPluginCheck();
			boolean isDomChanged=true;
			if (domMutationNotifierPluginCheck) {
				// One DomMutationNotifierPlugin has been instantiated

				isDomChanged = CrawljaxPluginsUtil.runDomMutationNotifierPlugin( browser );
			}

			if (domMutationNotifierPluginCheck==true && isDomChanged == false) {
				// One domMutationNotifierPlugin has been instantiated
				// Dom has not been changed so a time consuming Dom compare is not needed.
				return ClickResult.domUnChanged;

			}
			else {
				// Either there is no domMutationNotifierPluginCheck or
				// Dom might have been changed. Thus a complete Dom comparison must be done

			//    if (isDomChanged(this.getStateMachine().getCurrentState(), newState)) {

				if (CrawljaxPluginsUtil.runDomChangeNotifierPlugin(this.getStateMachine().getCurrentState(), eventable, newState)) {

					// Dom is changed, so data might need be filled in again

					controller.getSession().addEventableToCrawlPath(eventable);
					if (this.getStateMachine().update(eventable, newState, this.getBrowser(),
							this.controller.getSession())) {
						// Dom changed
						// No Clone
						// TODO change the interface of runGuidedCrawlingPlugins to remove the
						// controller.getSession().getCurrentCrawlPath() call because its from the
						// session now.
						CrawljaxPluginsUtil.runGuidedCrawlingPlugins(controller, controller
								.getSession(), controller.getSession().getCurrentCrawlPath(), this
								.getStateMachine());

						return ClickResult.newState;
					} else {
						// Dom changed; Clone
						return ClickResult.cloneDetected;
					}

				}

			}

		}

		// Event not fired or, Dom not changed

		// Amin: updating event productivity ratio for self-loop events or not fired ones
		if (controller.isEfficientCrawling())
			controller.getSession().getStateFlowGraph().updateEventProductivity(eventable, null);

		return ClickResult.domUnChanged;
	}

	/**
	 * Return the Exacteventpath.
	 *
	 * @return the exacteventpath
	 * @deprecated use {@link CrawlSession#getCurrentCrawlPath()}
	 */
	@Deprecated
	public final List<Eventable> getExacteventpath() {
		return controller.getSession().getCurrentCrawlPath();
	}

	/**
	 * Have we reached the depth limit?
	 *
	 * @param depth
	 *            the current depth. Added as argument so this call can be moved out if desired.
	 * @return true if the limit has been reached
	 */
	private boolean depthLimitReached(int depth) {

		if (this.depth >= configurationReader.getCrawlSpecificationReader().getDepth()
		        && configurationReader.getCrawlSpecificationReader().getDepth() != 0) {
			LOGGER.info("DEPTH " + depth + " reached returning from rec call. Given depth: "
			        + configurationReader.getCrawlSpecificationReader().getDepth());
			return true;
		} else {
			return false;
		}
	}

	private void spawnThreads(StateVertix state) {
		Crawler c = null;
		do {
			if (c != null) {
				this.crawlQueueManager.addWorkToQueue(c);
			}
			c =
			        new Crawler(this.controller, controller.getSession().getCurrentCrawlPath()
			                .immutableCopy(true));
		} while (state.registerCrawler(c));
	}

	private ClickResult crawlAction(CandidateCrawlAction action) throws CrawljaxException {
		CandidateElement candidateElement = action.getCandidateElement();
		EventType eventType = action.getEventType();

		StateVertix orrigionalState = this.getStateMachine().getCurrentState();

		if (candidateElement.allConditionsSatisfied(getBrowser())) {
			ClickResult clickResult = clickTag(new Eventable(candidateElement, eventType));

			switch (clickResult) {
				case cloneDetected:
					fired = false;
					// We are in the clone state so we continue with the cloned version to search for work.
					this.controller.getSession().branchCrawlPath();
					spawnThreads(orrigionalState);
					break;
				case newState:
					fired = true;
					// Recurse because new state found
					spawnThreads(orrigionalState);

					//Amin: This is not used anymore due to single-threading diverse crawling
					//LOGGER.info("New state found!");
					// Amin: pause crawling if diverse crawling is set to true and if all browsers are opened
					//if (controller.isDiverseCrawling() && controller.allBrowsersOpened()){
					//	LOGGER.info("Thread will be paused for diverse crawling...");
					//	pauseFlag = true;
					//	pauseCrawling();
					//}

					break;
				case domUnChanged:
					// Dom not updated, continue with the next
					break;
				default:
					break;
			}
			return clickResult;
		} else {

			LOGGER.info("Conditions not satisfied for element: " + candidateElement + "; State: "
			        + this.getStateMachine().getCurrentState().getName());
		}
		return ClickResult.domUnChanged;
	}

	/**
	 * Crawl through the clickables.
	 *
	 * @throws CrawljaxException
	 *             if an exception is thrown.
	 */
	private boolean crawl() throws CrawljaxException {
		if (depthLimitReached(depth)) {
			return true;
		}

		if (!checkConstraints()) {
			return false;
		}

		// Store the currentState to be able to 'back-track' later.
		StateVertix orrigionalState = this.getStateMachine().getCurrentState();

		if (orrigionalState.searchForCandidateElements(candidateExtractor,
				configurationReader.getTagElements(), configurationReader.getExcludeTagElements(),
				configurationReader.getCrawlSpecificationReader().getClickOnce(),
				controller.getSession().getStateFlowGraph(), controller.isEfficientCrawling(), controller.isRandomEventExec())) {
			// Only execute the preStateCrawlingPlugins when it's the first time
			LOGGER.info("Starting preStateCrawlingPlugins...");

			List<CandidateElement> candidateElements = orrigionalState.getUnprocessedCandidateElements();

			// Amin: just for logging
			// LOGGER.info("INNER # candidateElements for state " + orrigionalState.getName() + " is " + candidateElements.size());

			CrawljaxPluginsUtil.runPreStateCrawlingPlugins(controller.getSession(),	candidateElements);
			// update crawlActions
			orrigionalState.filterCandidateActions(candidateElements);

			// Amin: This is the count of candidates after filtering...
			CrawljaxController.NumCandidateClickables += orrigionalState.getNumCandidateElements();
		}else
			// Amin: just for logging
			// LOGGER.info("Outer # candidateElements for state " + orrigionalState.getName() + " is ZERO!");

		// Amin: check if there were not candidateElements for the current state (i.e., is leaf node)
		if (orrigionalState.isFullyExpanded())
			this.controller.getSession().getStateFlowGraph().removeFromNotFullExpandedStates(orrigionalState);

		CandidateCrawlAction action =
			orrigionalState.pollCandidateCrawlAction(this, crawlQueueManager);


		while (action != null) {
			if (depthLimitReached(depth)) {
				return true;
			}

			if (!checkConstraints()) {
				return false;
			}
			ClickResult result = this.crawlAction(action);

			orrigionalState.finishedWorking(this, action);

			switch (result) {
				case newState:
					boolean detected = newStateDetected(orrigionalState);
					return detected;
				case cloneDetected:
					return true;
				default:
					break;
			}

			action = orrigionalState.pollCandidateCrawlAction(this, crawlQueueManager);
		}
		return true;
	}


	/**
	 * Added by Amin for diverse crawling
	 * This is to pause a crawler after each time a new state is detected.
	 */
	void pauseCrawling(){
		try {
			synchronized(this){
				while(pauseFlag){// should not be here!!
					LOGGER.info("Wait until notified...");
					controller.addToWaitingCrawlerList(this);
					wait();
					pauseFlag = false;
					LOGGER.info("Resume crawling!");
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Added by Amin for diverse crawling
	 * This is to notify a crawler to resume if its state is diverse.
	 */
	void resumeCrawling(){
		try {
			synchronized(this){
				pauseFlag = false;
				System.out.println(name);
				LOGGER.info("Notified " + this.name + " to continue crawling!");
				notify();
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * A new state has been found!
	 *
	 * @param orrigionalState
	 *            the current state
	 * @return true if crawling must continue false otherwise.
	 * @throws CrawljaxException
	 */
	private boolean newStateDetected(StateVertix orrigionalState) throws CrawljaxException {

		/**
		 * An event has been fired so we are one level deeper
		 */

		depth++;
		LOGGER.info("RECURSIVE Call crawl; Current DEPTH= " + depth);
		if (!this.crawl()) {
			// Crawling has stopped
			controller.terminate(false);
			return false;
		}

		this.getStateMachine().changeState(orrigionalState);
		return true;
	}

	/**
	 * Initialize the Crawler, retrieve a Browser and go to the initial URL when no browser was
	 * present. rewind the state machine and goBack to the state if there is exactEventPath is
	 * specified.
	 *
	 * @throws InterruptedException
	 *             when the request for a browser is interrupted.
	 */
	public void init() throws InterruptedException {
		// Start a new CrawlPath for this Crawler
		controller.getSession().startNewPath();

		this.browser = this.getBrowser();
		if (this.browser == null) {
			/**
			 * As the browser is null, request one and got to the initial URL, if the browser is
			 * Already set the browser will be in the initial URL.
			 */
			this.browser = controller.getBrowserPool().requestBrowser();
			LOGGER.info("Reloading page for navigating back");
			this.goToInitialURL();
		}
		// TODO Stefan ideally this should be placed in the constructor
		this.formHandler =
		        new FormHandler(getBrowser(), configurationReader.getInputSpecification(),
		                configurationReader.getCrawlSpecificationReader().getRandomInputInForms());

		this.candidateExtractor =
		        new CandidateElementExtractor(controller.getElementChecker(), this.getBrowser(),
		                formHandler, configurationReader.getCrawlSpecificationReader());
		/**
		 * go back into the previous state.
		 */
		try {
			this.goBackExact();
		} catch (CrawljaxException e) {
			LOGGER.error("Failed to backtrack", e);
		}
	}

	/**
	 * Terminate and clean up this Crawler, release the acquired browser. Notice that other Crawlers
	 * might still be active. So this function does NOT shutdown all Crawlers active that should be
	 * done with {@link CrawlerExecutor#shutdown()}
	 */
	public void shutdown() {
		controller.getBrowserPool().freeBrowser(this.getBrowser());
	}

	/**
	 * The main function stated by the ExecutorService. Crawlers add themselves to the list by
	 * calling {@link CrawlQueueManager#addWorkToQueue(Crawler)}. When the ExecutorService finds a
	 * free thread this method is called and when this method ends the Thread is released again and
	 * a new Thread is started
	 *
	 * @see java.util.concurrent.Executors#newFixedThreadPool(int)
	 * @see java.util.concurrent.ExecutorService
	 */
	@Override
	public void run() {
		if (!checkConstraints()) {
			// Constrains are not met at start of this Crawler, so stop immediately
			return;
		}
		if (backTrackPath.last() != null) {
			try {
				if (!backTrackPath.last().getTargetStateVertix().startWorking(this)) {
					return;
				}
			} catch (CrawljaxException e) {
				LOGGER.error("Received Crawljax exception", e);
			}
		}
		try {
			/**
			 * Init the Crawler
			 */
			try {
				this.init();
			} catch (InterruptedException e) {
				if (this.getBrowser() == null) {
					return;
				}
			}

			/**
			 * Hand over the main crawling
			 */
			// Amin: set strategicCrawl to true for enabling strategies for crawling
			//strategicCrawl = true;

			if (strategicCrawl){
				if (!this.guidedCrawl()) {
					controller.terminate(false);
				}
			}else{
				//this is the default Crawljax in DFS fashion
				if (!this.crawl()) {
					controller.terminate(false);
				}
			}

			/**
			 * Crawling is done; so the crawlPath of the current branch is known
			 */
			if (!fired) {
				controller.getSession().removeCrawlPath();
			}
		} catch (BrowserConnectionException e) {
			// The connection of the browser has gone down, most of the times it means that the
			// browser process has crashed.
			LOGGER.error("Crawler failed because the used browser died during Crawling",
			        new CrawlPathToException("Crawler failed due to browser crash", controller
			                .getSession().getCurrentCrawlPath(), e));
			// removeBrowser will throw a RuntimeException if the current browser is the last
			// browser in the pool.
			this.controller.getBrowserPool().removeBrowser(this.getBrowser(),
			        this.controller.getCrawlQueueManager());
			return;
		} catch (CrawljaxException e) {
			LOGGER.error("Crawl failed!", e);
		}
		/**
		 * At last failure or non shutdown the Crawler.
		 */
		this.shutdown();
	}

	/**
	 * Return the browser used in this Crawler Thread.
	 *
	 * @return the browser used in this Crawler Thread
	 */
	public EmbeddedBrowser getBrowser() {
		return browser;
	}

	@Override
	public String toString() {
		return this.name;
	}

	/**
	 * @return the state machine.
	 */
	public StateMachine getStateMachine() {
		return stateMachine;
	}

	/**
	 * Test to see if the (new) DOM is changed with regards to the old DOM. This method is Thread
	 * safe.
	 *
	 * @param stateBefore
	 *            the state before the event.
	 * @param stateAfter
	 *            the state after the event.
	 * @return true if the state is changed according to the compare method of the oracle.
	 */
	private boolean isDomChanged(final StateVertix stateBefore, final StateVertix stateAfter) {
		boolean isChanged = false;

		// do not need Oracle Comparators now, because hash of stripped dom is
		// already calculated
		// isChanged = !stateComparator.compare(stateBefore.getDom(),
		// stateAfter.getDom(), browser);
		isChanged = !stateAfter.equals(stateBefore);
		if (isChanged) {
			LOGGER.info("Dom is Changed!");
		} else {
			LOGGER.info("Dom Not Changed!");
		}

		return isChanged;
	}

	/**
	 * Checks the state and time constraints. This function is nearly Thread-safe.
	 *
	 * @return true if all conditions are met.
	 */
	private boolean checkConstraints() {
		long timePassed = System.currentTimeMillis() - controller.getSession().getStartTime();
		int maxCrawlTime = configurationReader.getCrawlSpecificationReader().getMaximumRunTime();
		if ((maxCrawlTime != 0) && (timePassed > maxCrawlTime * ONE_SECOND)) {

			LOGGER.info("Max time " + maxCrawlTime + " seconds passed!");
			/* stop crawling */
			return false;
		}
		StateFlowGraph graph = controller.getSession().getStateFlowGraph();
		int maxNumberOfStates =
		        configurationReader.getCrawlSpecificationReader().getMaxNumberOfStates();
		if ((maxNumberOfStates != 0) && (graph.getAllStates().size() >= maxNumberOfStates)) {
			LOGGER.info("Max number of states " + maxNumberOfStates + " reached!");
			/* stop crawling */
			return false;
		}
		/* continue crawling */
		return true;
	}



	/**
	 * Amin
	 * Intelligent crawling through the clickables.
	 *
	 * @throws CrawljaxException
	 *             if an exception is thrown.
	 */
	private boolean guidedCrawl() throws CrawljaxException {
		StateVertix orrigionalState = this.getStateMachine().getCurrentState();

		while (true) {
			//TODO: should check depth...
			if (depthLimitReached(depth)) {
				return true;
			}

			if (!checkConstraints()) {
				return false;
			}

			// Store the currentState to be able to 'back-track' later.
			System.out.println("orrigionalState is " + orrigionalState.getName());

			if (orrigionalState.searchForCandidateElements(candidateExtractor,
					configurationReader.getTagElements(), configurationReader.getExcludeTagElements(),
					configurationReader.getCrawlSpecificationReader().getClickOnce(),
					controller.getSession().getStateFlowGraph(), controller.isEfficientCrawling(), controller.isRandomEventExec())) {
				// Only execute the preStateCrawlingPlugins when it's the first time
				LOGGER.info("Starting preStateCrawlingPlugins...");

				List<CandidateElement> candidateElements = orrigionalState.getUnprocessedCandidateElements();

				//LOGGER.info("INNER # candidateElements for state " + orrigionalState.getName() + " is " + candidateElements.size());

				CrawljaxPluginsUtil.runPreStateCrawlingPlugins(controller.getSession(),	candidateElements);
				// update crawlActions
				orrigionalState.filterCandidateActions(candidateElements);

				// Amin: This is the count of candidates after filtering...
				CrawljaxController.NumCandidateClickables += orrigionalState.getNumCandidateElements();
			}else
				//LOGGER.info("Outer # candidateElements for state " + orrigionalState.getName() + " is ZERO!");

			// check if there were not candidateElements for the current state (i.e., is leaf node)
			if (orrigionalState.isFullyExpanded())
				this.controller.getSession().getStateFlowGraph().removeFromNotFullExpandedStates(orrigionalState);
			else{
				CandidateCrawlAction action =
					orrigionalState.pollCandidateCrawlAction(this, crawlQueueManager);

				ClickResult result = this.crawlAction(action);

				orrigionalState.finishedWorking(this, action);

				switch (result) {
				case newState:
					System.out.println("backTrackPath for new state " +this.getStateMachine().getCurrentState().getName()+ " is " + backTrackPath);
					this.getStateMachine().getCurrentState().setCrawlPathToState(controller.getSession().getCurrentCrawlPath());

					System.out.println("newState");
					break;
				case cloneDetected:
					System.out.println("cloneDetected");
					break;
				default:
					System.out.println("noChange");
					break;
				}
			}

			StateVertix currentState = this.getStateMachine().getCurrentState();
			StateVertix previousState = this.getStateMachine().getPreviousState();
			StateVertix previousPreviousState = this.getStateMachine().getPreviousPreviousState();

			//LOGGER.info("currentState is " + currentState);
			//LOGGER.info("previousState is " + previousState);
			//LOGGER.info("previousPreviousState is " + previousPreviousState);

			StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
			ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();

			// setting the crawl strategy
			CrawlStrategy strategy = CrawlStrategy.BFS;

			// choose next state to crawl based on the strategy
			StateVertix nextToCrawl = nextStateToCrawl(strategy);

			if (nextToCrawl==null){
				LOGGER.info("Something is wrong! nextToCrawl is null...");
				return false;
			}

			LOGGER.info("Next state to crawl is: " + nextToCrawl.getName());

			switch (strategy){
				case DFS:
					if (!notFullExpandedStates.contains(currentState)){  // if the new state is fully expanded
						LOGGER.info("changing original from " + currentState.getName());
						this.getStateMachine().changeToNewState(nextToCrawl);
						LOGGER.info(" to " + this.getStateMachine().getCurrentState().getName());

						// Start a new CrawlPath for this Crawler
						controller.getSession().startNewPath();
						LOGGER.info("Reloading page for navigating back");
						this.goToInitialURL();
						reloadToSate(nextToCrawl); // backtrack
					}
					break;
				case BFS:
					if (nextToCrawl.equals(currentState)){
						// For BFS, this means event did not create new state, so continue with the same state.
						LOGGER.info("same state will be crawled for the next clickable...");
					}else{
						LOGGER.info("changing original from " + currentState.getName());
						this.getStateMachine().changeToNewState(nextToCrawl);
						LOGGER.info(" to " + this.getStateMachine().getCurrentState().getName());

						// Start a new CrawlPath for this Crawler
						controller.getSession().startNewPath();
						LOGGER.info("Reloading page for navigating back");
						this.goToInitialURL();
						reloadToSate(nextToCrawl); // backtrack
					}
					break;
				case Rand:
					if (nextToCrawl.equals(currentState)){
						// do nothing
						LOGGER.info("same path will be continued...");
					}else{
						LOGGER.info("changing original from " + currentState.getName());
						this.getStateMachine().changeToNewState(nextToCrawl);
						LOGGER.info(" to " + this.getStateMachine().getCurrentState().getName());

						// Start a new CrawlPath for this Crawler
						controller.getSession().startNewPath();
						LOGGER.info("Reloading page for navigating back");
						this.goToInitialURL();
						reloadToSate(nextToCrawl); // backtrack
					}
					break;
				case Div:
					if (nextToCrawl.equals(currentState)){
						// do nothing
						LOGGER.info("same path will be continued...");
					}else{
						LOGGER.info("changing original from " + currentState.getName());
						this.getStateMachine().changeToNewState(nextToCrawl);
						LOGGER.info(" to " + this.getStateMachine().getCurrentState().getName());

						// Start a new CrawlPath for this Crawler
						controller.getSession().startNewPath();
						LOGGER.info("Reloading page for navigating back");
						this.goToInitialURL();
						reloadToSate(nextToCrawl); // backtrack
					}
					break;
				default:
					System.out.println("Nothing!");
					break;
			}

			orrigionalState = this.getStateMachine().getCurrentState();
		}
	}

	/**
	 * Amin
	 * Reload the browser to the given state.
	 *
	 * @throws CrawljaxException
	 *             if the {@link Eventable#getTargetStateVertix()} encounters an error.
	 */
	private void reloadToSate(StateVertix s) throws CrawljaxException {
		/**
		 * Thread safe
		 */
		StateVertix curState = controller.getSession().getInitialState();

		LOGGER.info("reloading to state " + s.getName());

		if (s.equals(curState)){   // no Eventable to execute for the index state
			LOGGER.info("reloaded to index! no execution needed!!!");
			return;
		}

		LOGGER.info("crawlpath to state " + s.getName() + " is  " + s.getCrawlPathToState());

		for (Eventable clickable : s.getCrawlPathToState()) {

			if (!controller.getElementChecker().checkCrawlCondition(getBrowser())) {
				return;
			}

			LOGGER.info("Backtracking by executing " + clickable.getEventType() + " on element: "
			        + clickable);

			this.getStateMachine().changeState(clickable.getTargetStateVertix());

			curState = clickable.getTargetStateVertix();

			controller.getSession().addEventableToCrawlPath(clickable);

			this.handleInputElements(clickable);

			if (this.fireEvent(clickable)) {
				depth++;

				/**
				 * Run the onRevisitStateValidator(s)
				 */
				CrawljaxPluginsUtil.runOnRevisitStatePlugins(this.controller.getSession(),
				        curState);
			}

			if (!controller.getElementChecker().checkCrawlCondition(getBrowser())) {
				return;
			}
		}
	}



	/**
	 * Amin
	 * Find which state to crawl next
	 */
	public StateVertix nextStateToCrawl(CrawlStrategy strategy){
		int index = 0;
		StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
		ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();

		switch (strategy){
		case DFS:
			// continue with the last-in state
			if (notFullExpandedStates.size()>0)
				index = notFullExpandedStates.size()-1;
			break;
		case BFS:
			// next state is the first-in state
			index = 0;
			break;
		case Rand:
			Random randomGenerator = new Random();
			if (notFullExpandedStates.size()>0)
				index = randomGenerator.nextInt(notFullExpandedStates.size());
			break;
		case Div:
			if (notFullExpandedStates.size()>0){
				//index = nextForDiverseCrawlingCovOnly();
				//index = nextForDiverseCrawlingDDOnly();
				//index = nextForDiverseCrawlingPDOnly();

				//index = nextForDiverseCrawlingCo_PD(1.0, 1.0);
				//index = nextForDiverseCrawlingCo_DD(1.0, 1.0);
				//index = nextForDiverseCrawling(1.0, 1.0);  // PD+DD

				index = nextForDiverseCrawling3(1.0,1.0,1.0); // all
			}
			break;
		default:
			break;
		}

		// Amin: TODO: Check for the index
		if (index==-1)
			return null;

		if (notFullExpandedStates.size()==0)
			return null;

		LOGGER.info("Satet " + notFullExpandedStates.get(index) + " selected as the nextStateToCrawl");

		return notFullExpandedStates.get(index);
	}

	/**
	 * Added by Amin
	 * This is a main method for diverse crawling which decides about which state to crawl next.
	 * It calculates pair-wise diversity for states of waiting crawlers
	 *
	 * 	 @param PD_weight
	 *            the user defined weight for path-diversity
	 *   @param DD_weight
	 *            the user defined weight for DOM-diversity
	 */
	public int nextForDiverseCrawling(double PD_weight, double DD_weight){
		StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
		ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();
		int index = 0;

		ArrayList<Double> minPathDiv = new ArrayList<Double>();
		double minDD, AvgMinDD=0.0;

		ArrayList<Double> minDOMDiv = new ArrayList<Double>();
		double minPD=1.0, PD, AvgMinPD=0.0;

		// calculating minimum pair-wise Path-diversity and pair-wise DOM-diversity
		// Amin: TODO may need to check path-diversity w.r.t all states in the SFG
		for (int i=0; i < notFullExpandedStates.size(); i++){
			minDD = sfg.getMinDOMDiversity(notFullExpandedStates.get(i));
			//LOGGER.info("MinDD of state " +  notFullExpandedStates.get(i).getName() + " is " + minDD);

			for (int j=0; j < notFullExpandedStates.size(); j++){
				if (notFullExpandedStates.get(i)!=notFullExpandedStates.get(j)){
					PD = sfg.getPathDiversity(notFullExpandedStates.get(i), notFullExpandedStates.get(j));

					//LOGGER.info("PD of state " +  notFullExpandedStates.get(i).getName()
					//		+ " and state " +  notFullExpandedStates.get(j).getName() + " is " + PD);
					if (PD < minPD)
						minPD = PD;
				}
			}

			//LOGGER.info("minPD of state " +  notFullExpandedStates.get(i).getName() + " is " + minPD);
			minPathDiv.add(minPD);
			AvgMinPD += minPD;
			minPD=1.0;
			//LOGGER.info("minDD of state " +  notFullExpandedStates.get(i).getName() + " is " + minDD);
			minDOMDiv.add(minDD);
			AvgMinDD += minDD;
			minDD=1.0;
		}
		AvgMinPD /= notFullExpandedStates.size();
		//LOGGER.info("AvgMinPD is " +  AvgMinPD);
		AvgMinDD /= notFullExpandedStates.size();
		//LOGGER.info("AvgMinDD is " +  AvgMinDD);

		double winnerScore = 0.0;
		double stateScore = 0.0;

		for (int i=0; i < notFullExpandedStates.size(); i++){
			stateScore = PD_weight*minPathDiv.get(i) + DD_weight*minDOMDiv.get(i);
			//Amin: The idea is changed to select the one with the max score
			//if (stateScore >= AvgTotalDiv){
			if (stateScore >= winnerScore){
				winnerScore = stateScore;
				index = i;
			}
		}

		LOGGER.info("The winner state is " +  notFullExpandedStates.get(index).getName()
				+ " with diversity score "  + (PD_weight*minPathDiv.get(index) + DD_weight*minDOMDiv.get(index)));

		return index;
	}


	/**
	 * Added by Amin : coverage
	 */
	public int nextForDiverseCrawlingCovOnly(){
		StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
		ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();
		int index = 0;

		if (notFullExpandedStates.size()==0)
			return -1;

		double coverageIncrease;

		double winnerCoverageIncrease = 0.0;

		for (int i=0; i < notFullExpandedStates.size(); i++){
			coverageIncrease = sfg.getCoverageIncrease(notFullExpandedStates.get(i));

			if (coverageIncrease >= winnerCoverageIncrease){
				winnerCoverageIncrease = coverageIncrease;
				index = i;
			}
		}

		LOGGER.info("The winner state is " +  notFullExpandedStates.get(index).getName()
				+ " with coverageIncrease "  + winnerCoverageIncrease);

		return index;
	}




	/**
	 * Added by Amin
	 * Combining nextForDiverseCrawling2 and nextForDiverseCrawling2
	 *
	 * 	 @param PD_weight
	 *            the user defined weight for path-diversity
	 *   @param DD_weight
	 *            the user defined weight for DOM-diversity
	 */
	public int nextForDiverseCrawling3(double Cov_weight, double PD_weight, double DD_weight){
		StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
		ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();
		int index = 0;

		ArrayList<Double> minPathDiv = new ArrayList<Double>();
		ArrayList<Double> minDOMDiv = new ArrayList<Double>();
		double minPD=1.0, PD, AvgMinPD=0.0;
		double minDD, AvgMinDD=0.0;

		// calculating minimum pair-wise Path-diversity and pair-wise DOM-diversity
		// Amin: TODO may need to check path-diversity w.r.t all states in the SFG
		for (int i=0; i < notFullExpandedStates.size(); i++){
			minDD = sfg.getMinDOMDiversity(notFullExpandedStates.get(i));
			//LOGGER.info("MinDD of state " +  notFullExpandedStates.get(i).getName() + " is " + minDD);

			for (int j=0; j < notFullExpandedStates.size(); j++){
				if (notFullExpandedStates.get(i)!=notFullExpandedStates.get(j)){
					PD = sfg.getPathDiversity(notFullExpandedStates.get(i), notFullExpandedStates.get(j));
					//LOGGER.info("PD of state " +  notFullExpandedStates.get(i).getName()
					//		+ " and state " +  notFullExpandedStates.get(j).getName() + " is " + PD);
					if (PD < minPD)
						minPD = PD;
				}
			}

			//LOGGER.info("minPD of state " +  notFullExpandedStates.get(i).getName() + " is " + minPD);
			minPathDiv.add(minPD);
			AvgMinPD += minPD;
			minPD=1.0;
			//LOGGER.info("minDD of state " +  notFullExpandedStates.get(i).getName() + " is " + minDD);
			minDOMDiv.add(minDD);
			AvgMinDD += minDD;
			minDD=1.0;
		}
		AvgMinPD /= notFullExpandedStates.size();
		//LOGGER.info("AvgMinPD is " +  AvgMinPD);
		AvgMinDD /= notFullExpandedStates.size();
		//LOGGER.info("AvgMinDD is " +  AvgMinDD);

		double winnerScore = 0.0;
		double stateScore = 0.0;
		double coverageIncrease = 0.0;
		double winnerCoverageIncrease = 0.0;

		for (int i=0; i < notFullExpandedStates.size(); i++){
			coverageIncrease = sfg.getCoverageIncrease(notFullExpandedStates.get(i));

			stateScore = Cov_weight*coverageIncrease + PD_weight*minPathDiv.get(i) + DD_weight*minDOMDiv.get(i);

			if (stateScore >= winnerScore){
				winnerScore = stateScore;
				winnerCoverageIncrease = coverageIncrease;
				index = i;
			}
		}

		LOGGER.info("The winner state is " +  notFullExpandedStates.get(index).getName()
				+ " with score "  + (Cov_weight*winnerCoverageIncrease + PD_weight*minPathDiv.get(index) + DD_weight*minDOMDiv.get(index)));

		return index;
	}


	/**
	 * Added by Amin
	 * This is a main method for diverse crawling which decides about which state to crawl next.
	 * It calculates pair-wise diversity for states of waiting crawlers
	 *
	 * 	 @param PD_weight
	 *            the user defined weight for path-diversity
	 *   @param DD_weight
	 *            the user defined weight for DOM-diversity
	 */
	public int nextForDiverseCrawlingDDOnly(){
		StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
		ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();
		int index = 0;

		double minDD, AvgMinDD=0.0;

		ArrayList<Double> minDOMDiv = new ArrayList<Double>();

		// calculating minimum pair-wise Path-diversity and pair-wise DOM-diversity
		// Amin: TODO may need to check path-diversity w.r.t all states in the SFG
		for (int i=0; i < notFullExpandedStates.size(); i++){
			minDD = sfg.getMinDOMDiversity(notFullExpandedStates.get(i));
			//LOGGER.info("MinDD of state " +  notFullExpandedStates.get(i).getName() + " is " + minDD);

			minDOMDiv.add(minDD);
			AvgMinDD += minDD;
			minDD=1.0;
		}

		AvgMinDD /= notFullExpandedStates.size();
		//LOGGER.info("AvgMinDD is " +  AvgMinDD);
		// average total diversity score
		double winnerScore = 0.0;
		double stateScore = 0.0;

		for (int i=0; i < notFullExpandedStates.size(); i++){
			stateScore = minDOMDiv.get(i);
			//Amin: The idea is changed to select the one with the max score
			//if (stateScore >= AvgTotalDiv){
			if (stateScore >= winnerScore){
				winnerScore = stateScore;
				index = i;
			}
		}

		LOGGER.info("The winner state is " +  notFullExpandedStates.get(index).getName()
				+ " with DOM diversity score "  + minDOMDiv.get(index));

		return index;
	}



	/**
	 * Added by Amin
	 * This is a main method for diverse crawling which decides about which state to crawl next.
	 * It calculates pair-wise diversity for states of waiting crawlers
	 */
	public int nextForDiverseCrawlingPDOnly(){
		StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
		ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();
		int index = 0;

		ArrayList<Double> minPathDiv = new ArrayList<Double>();
		double minPD=1.0, PD, AvgMinPD=0.0;

		// calculating minimum pair-wise Path-diversity and pair-wise DOM-diversity
		// Amin: TODO may need to check path-diversity w.r.t all states in the SFG
		for (int i=0; i < notFullExpandedStates.size(); i++){

			for (int j=0; j < notFullExpandedStates.size(); j++){
				if (notFullExpandedStates.get(i)!=notFullExpandedStates.get(j)){
					PD = sfg.getPathDiversity(notFullExpandedStates.get(i), notFullExpandedStates.get(j));

					//LOGGER.info("PD of state " +  notFullExpandedStates.get(i).getName()
					//		+ " and state " +  notFullExpandedStates.get(j).getName() + " is " + PD);
					if (PD < minPD)
						minPD = PD;
				}
			}

			//LOGGER.info("minPD of state " +  notFullExpandedStates.get(i).getName() + " is " + minPD);
			minPathDiv.add(minPD);
			AvgMinPD += minPD;
			minPD=1.0;

		}
		AvgMinPD /= notFullExpandedStates.size();
		//LOGGER.info("AvgMinPD is " +  AvgMinPD);

		double winnerScore = 0.0;
		double stateScore = 0.0;

		for (int i=0; i < notFullExpandedStates.size(); i++){
			stateScore = minPathDiv.get(i);
			//Amin: The idea is changed to select the one with the max score
			//if (stateScore >= AvgTotalDiv){
			if (stateScore >= winnerScore){
				winnerScore = stateScore;
				index = i;
			}
		}

		LOGGER.info("The winner state is " +  notFullExpandedStates.get(index).getName()
				+ " with PD diversity score "  + minPathDiv.get(index));

		return index;
	}



	/**
	 * Added by Amin
	 */
	public int nextForDiverseCrawlingCo_DD(double Cov_weight, double DD_weight){
		StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
		ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();
		int index = 0;

		ArrayList<Double> minDOMDiv = new ArrayList<Double>();
		double minDD, AvgMinDD=0.0;

		// calculating minimum pair-wise Path-diversity and pair-wise DOM-diversity
		// Amin: TODO may need to check path-diversity w.r.t all states in the SFG
		for (int i=0; i < notFullExpandedStates.size(); i++){
			minDD = sfg.getMinDOMDiversity(notFullExpandedStates.get(i));
			//LOGGER.info("MinDD of state " +  notFullExpandedStates.get(i).getName() + " is " + minDD);
			minDOMDiv.add(minDD);
			AvgMinDD += minDD;
			minDD=1.0;
		}
		AvgMinDD /= notFullExpandedStates.size();
		//LOGGER.info("AvgMinDD is " +  AvgMinDD);

		double winnerScore = 0.0;
		double stateScore = 0.0;
		double coverageIncrease = 0.0;
		double winnerCoverageIncrease = 0.0;

		for (int i=0; i < notFullExpandedStates.size(); i++){
			coverageIncrease = sfg.getCoverageIncrease(notFullExpandedStates.get(i));

			stateScore = Cov_weight*coverageIncrease + DD_weight*minDOMDiv.get(i);

			if (stateScore >= winnerScore){
				winnerScore = stateScore;
				winnerCoverageIncrease = coverageIncrease;
				index = i;
			}
		}

		LOGGER.info("The winner state is " +  notFullExpandedStates.get(index).getName()
				+ " with score "  + (Cov_weight*winnerCoverageIncrease + DD_weight*minDOMDiv.get(index)));

		return index;
	}


	/**
	 * Added by Amin
	 */
	public int nextForDiverseCrawlingCo_PD(double Cov_weight, double PD_weight){
		StateFlowGraph sfg = controller.getSession().getStateFlowGraph();
		ArrayList<StateVertix> notFullExpandedStates = sfg.getNotFullExpandedStates();
		int index = 0;

		ArrayList<Double> minPathDiv = new ArrayList<Double>();
		double minPD=1.0, PD, AvgMinPD=0.0;

		// calculating minimum pair-wise Path-diversity and pair-wise DOM-diversity
		// Amin: TODO may need to check path-diversity w.r.t all states in the SFG
		for (int i=0; i < notFullExpandedStates.size(); i++){
			//LOGGER.info("MinDD of state " +  notFullExpandedStates.get(i).getName() + " is " + minDD);

			for (int j=0; j < notFullExpandedStates.size(); j++){
				if (notFullExpandedStates.get(i)!=notFullExpandedStates.get(j)){
					PD = sfg.getPathDiversity(notFullExpandedStates.get(i), notFullExpandedStates.get(j));
					//LOGGER.info("PD of state " +  notFullExpandedStates.get(i).getName()
					//		+ " and state " +  notFullExpandedStates.get(j).getName() + " is " + PD);
					if (PD < minPD)
						minPD = PD;
				}
			}

			//LOGGER.info("minPD of state " +  notFullExpandedStates.get(i).getName() + " is " + minPD);
			minPathDiv.add(minPD);
			AvgMinPD += minPD;
			minPD=1.0;
		}
		AvgMinPD /= notFullExpandedStates.size();
		//LOGGER.info("AvgMinPD is " +  AvgMinPD);

		double winnerScore = 0.0;
		double stateScore = 0.0;
		double coverageIncrease = 0.0;
		double winnerCoverageIncrease = 0.0;

		for (int i=0; i < notFullExpandedStates.size(); i++){
			coverageIncrease = sfg.getCoverageIncrease(notFullExpandedStates.get(i));

			stateScore = Cov_weight*coverageIncrease + PD_weight*minPathDiv.get(i);

			if (stateScore >= winnerScore){
				winnerScore = stateScore;
				winnerCoverageIncrease = coverageIncrease;
				index = i;
			}
		}

		LOGGER.info("The winner state is " +  notFullExpandedStates.get(index).getName()
				+ " with score "  + (Cov_weight*winnerCoverageIncrease + PD_weight*minPathDiv.get(index)));

		return index;
	}

}
