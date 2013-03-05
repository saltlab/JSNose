package com.crawljax.core.state;

import static org.junit.Assert.assertEquals;
import net.jcip.annotations.GuardedBy;

import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.log4j.Logger;
import org.jgrapht.DirectedGraph;
import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.alg.KShortestPaths;
import org.jgrapht.graph.DirectedMultigraph;
import java.util.Stack;

import com.crawljax.core.CandidateElement;
import com.crawljax.core.CrawljaxEstimator;
import com.crawljax.util.TreeEditDist.RTED_InfoTree_Opt;
import com.google.common.util.concurrent.Service.State;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The State-Flow Graph is a directed graph with states on the vertices and clickables on the edges.
 * 
 * @author mesbah
 * @author aminmf
 * @version $Id: StateFlowGraph.java 446 2010-09-16 09:17:24Z slenselink@google.com $
 */
public class StateFlowGraph {
	private static final Logger LOGGER = Logger.getLogger(StateFlowGraph.class.getName());

	private final DirectedGraph<StateVertix, Eventable> sfg;

	/**
	 * Intermediate counter for the number of states, not relaying on getAllStates.size() because of
	 * Thread-safety.
	 */
	private final AtomicInteger stateCounter = new AtomicInteger(1);

	/** Added by Amin: 
	 *  statesDomDiversity is a hashtable which stores pair-wise DOM diversity of all sates in the SFG in the format <"si,sj",DD(si,sj)>
	 *  hashtables eventScore and eventCount are used to store and calculate event productivity ratio. They store string representation of an Element in an Eventable
	 */
	private Map<String,Double> statesDomDiversity = new Hashtable<String,Double>();
	private Map<String,Double> statesPathDiversity = new Hashtable<String,Double>();
	private Map<String,Double> statesCoverageIncrease = new Hashtable<String,Double>();
	private Map<String,Double> eventScore = new Hashtable<String,Double>();   // numerator
	private Map<String,Integer> eventCount = new Hashtable<String,Integer>(); // denominator
	private boolean diverseCrawling = false;
	private boolean efficientCrawling = false;
	private ArrayList<StateVertix> notFullExpandedStates = new ArrayList<StateVertix>();

	private static final int STATE_SPACE_SIZE = 10;  // should be set based on previous crawl of the whole state-space
	private CrawljaxEstimator estimator;

	//Amin: later remove
	boolean done = false;
	
	// Amin: These are set by the InitialCrawler
	public void setDiverseCrawling(boolean diverseCrawling) { 
		this.diverseCrawling = diverseCrawling;
	}
	public void setEfficientCrawling(boolean efficientCrawling) { 
		this.efficientCrawling = efficientCrawling;
	}	
	//Amin
	public ArrayList<StateVertix> getNotFullExpandedStates(){
		return notFullExpandedStates;
	}
	//Amin
	private double latestCoverage = 0.0;
	private double latestCoverageIncrease = 0.0;
	public void setLatestCoverage(double newCoverage){
		latestCoverageIncrease = newCoverage-latestCoverage;
		if (latestCoverageIncrease<0)
			latestCoverageIncrease=0; // sometimes in the beginning there is a high coverage, later new scripts are modified and thus coverage decreases
		latestCoverage = newCoverage;
	}	

	public void setInitialCoverage(StateVertix initState, double newCoverage){
		latestCoverageIncrease = newCoverage-latestCoverage;
		latestCoverage = newCoverage;
		setCoverageIncrease(initState);
	}		
	
	
	
	/**
	 * Empty constructor.
	 */
	public StateFlowGraph() {
		sfg = new DirectedMultigraph<StateVertix, Eventable>(Eventable.class);
	}

	/**
	 * The constructor.
	 * 
	 * @param initialState
	 *            the state to start from.
	 */
	public StateFlowGraph(StateVertix initialState) {
		this();
		sfg.addVertex(initialState);
		notFullExpandedStates.add(initialState);
		this.estimator = new CrawljaxEstimator(STATE_SPACE_SIZE);
	}

	/**
	 * Adds a state (as a vertix) to the State-Flow Graph if not already present. More formally,
	 * adds the specified vertex, v, to this graph if this graph contains no vertex u such that
	 * u.equals(v). If this graph already contains such vertex, the call leaves this graph unchanged
	 * and returns false. In combination with the restriction on constructors, this ensures that
	 * graphs never contain duplicate vertices. Throws java.lang.NullPointerException - if the
	 * specified vertex is null. This method automatically updates the state name to reflect the
	 * internal state counter.
	 * 
	 * @param stateVertix
	 *            the state to be added.
	 * @return the clone if one is detected null otherwise.
	 * @see org.jgrapht.Graph#addVertex(Object)
	 */
	public StateVertix addState(StateVertix stateVertix) {
		return addState(stateVertix, true);
	}

	/**
	 * Adds a state (as a vertix) to the State-Flow Graph if not already present. More formally,
	 * adds the specified vertex, v, to this graph if this graph contains no vertex u such that
	 * u.equals(v). If this graph already contains such vertex, the call leaves this graph unchanged
	 * and returns false. In combination with the restriction on constructors, this ensures that
	 * graphs never contain duplicate vertices. Throws java.lang.NullPointerException - if the
	 * specified vertex is null.
	 * 
	 * @param stateVertix
	 *            the state to be added.
	 * @param correctName
	 *            if true the name of the state will be corrected according to the internal state
	 *            counter.
	 * @return the clone if one is detected null otherwise.
	 * @see org.jgrapht.Graph#addVertex(Object)
	 */
	@GuardedBy("sfg")
	public StateVertix addState(StateVertix stateVertix, boolean correctName) {
		synchronized (sfg) {
			if (!sfg.addVertex(stateVertix)) {
				// Graph already contained the vertix
				return this.getStateInGraph(stateVertix);
			} else {
				/**
				 * A new State has been added so check to see if the name is correct, remember this
				 * is the only place states can be added and we are now locked so getAllStates.size
				 * works correctly.
				 */
				if (correctName) {
					// the -1 is for the "index" state.
					int totalNumberOfStates = this.getAllStates().size() - 1;
					String correctedName =
					        makeStateName(totalNumberOfStates, stateVertix.isGuidedCrawling());
					if (!stateVertix.getName().equals("index")
					        && !stateVertix.getName().equals(correctedName)) {
						LOGGER.info("Correcting state name from  " + stateVertix.getName()
						        + " to " + correctedName);
						stateVertix.setName(correctedName);
					}
				}
			}
			stateCounter.set(this.getAllStates().size() - 1);

			// Amin: Add the new state to the list of unexpanded states
			notFullExpandedStates.add(stateVertix);
			LOGGER.info("State " + stateVertix + " added to the notFullExpandedStates list!");
			
			// Amin: calculate pair-wise DOM diversity with respect to all states in the SFG
			if (diverseCrawling){
				setDOMDiversity(stateVertix);
				setPathDiversity(stateVertix);
				setCoverageIncrease(stateVertix);
			}
		}

		return null;
	}
	
	/**
	 * Amin: removing a state from notFullExpandedStates list if all candidate clickables are fired. 
	 */
	public void removeFromNotFullExpandedStates(StateVertix s){
		if (notFullExpandedStates.contains(s)){
			notFullExpandedStates.remove(s);
			LOGGER.info("State " + s.getName() + " removed from the notFullExpandedStates list!");
		}
		else
			LOGGER.info("State " + s.getName() + " does not exist in the notFullExpandedStates list!");
	}

	/**
	 * Adds the specified edge to this graph, going from the source vertex to the target vertex.
	 * More formally, adds the specified edge, e, to this graph if this graph contains no edge e2
	 * such that e2.equals(e). If this graph already contains such an edge, the call leaves this
	 * graph unchanged and returns false. Some graphs do not allow edge-multiplicity. In such cases,
	 * if the graph already contains an edge from the specified source to the specified target, than
	 * this method does not change the graph and returns false. If the edge was added to the graph,
	 * returns true. The source and target vertices must already be contained in this graph. If they
	 * are not found in graph IllegalArgumentException is thrown.
	 * 
	 * @param sourceVert
	 *            source vertex of the edge.
	 * @param targetVert
	 *            target vertex of the edge.
	 * @param clickable
	 *            the clickable edge to be added to this graph.
	 * @return true if this graph did not already contain the specified edge.
	 * @see org.jgrapht.Graph#addEdge(Object, Object, Object)
	 */
	@GuardedBy("sfg")
	public boolean addEdge(StateVertix sourceVert, StateVertix targetVert, Eventable clickable) {
		synchronized (sfg) {
			// TODO Ali; Why is this code (if-stmt) here? Its the same as what happens in sfg.addEge
			// imo (21-01-10 Stefan).
			if (sfg.containsEdge(sourceVert, targetVert)
			        && sfg.getAllEdges(sourceVert, targetVert).contains(clickable)) {
				return false;
			}
			return sfg.addEdge(sourceVert, targetVert, clickable);
		}
	}

	/**
	 * @return the string representation of the graph.
	 * @see org.jgrapht.DirectedGraph#toString()
	 */
	@Override
	public String toString() {
		return sfg.toString();
	}

	/**
	 * Returns a set of all clickables outgoing from the specified vertex.
	 * 
	 * @param stateVertix
	 *            the state vertix.
	 * @return a set of the outgoing edges (clickables) of the stateVertix.
	 * @see org.jgrapht.DirectedGraph#outgoingEdgesOf(Object)
	 */
	public Set<Eventable> getOutgoingClickables(StateVertix stateVertix) {
		return sfg.outgoingEdgesOf(stateVertix);
	}

	/**
	 * Returns a set of all edges incoming into the specified vertex.
	 * 
	 * @param stateVertix
	 *            the state vertix.
	 * @return a set of the incoming edges (clickables) of the stateVertix.
	 * @see org.jgrapht.DirectedGraph#incomingEdgesOf(Object)
	 */
	public Set<Eventable> getIncomingClickable(StateVertix stateVertix) {
		return sfg.incomingEdgesOf(stateVertix);
	}

	/**
	 * Returns the set of outgoing states.
	 * 
	 * @param stateVertix
	 *            the state.
	 * @return the set of outgoing states from the stateVertix.
	 */
	public Set<StateVertix> getOutgoingStates(StateVertix stateVertix) {
		final Set<StateVertix> result = new HashSet<StateVertix>();

		for (Eventable c : getOutgoingClickables(stateVertix)) {
			result.add(sfg.getEdgeTarget(c));
		}

		return result;
	}

	
	/**
	 * Calculates the number of unprocessed candidate elements for all states in the graph
	 * -- Added by Amin
	 * 
	 * @return the count of unprocessed candidate elements in the StateFlowGraph states
	 */
	public int getNumUnprocessedCandidateElements() {
		Set<StateVertix> states = getAllStates();
		int count = 0;
		
		for (StateVertix st : states) {
			count += st.getUnprocessedCandidateElements().size();
		}
		return count;
	}
	
	
	/**
	 * @param clickable
	 *            the edge.
	 * @return the target state of this edge.
	 */
	public StateVertix getTargetState(Eventable clickable) {
		return sfg.getEdgeTarget(clickable);
	}

	/**
	 * Is it possible to go from s1 -> s2?
	 * 
	 * @param source
	 *            the source state.
	 * @param target
	 *            the target state.
	 * @return true if it is possible (edge exists in graph) to go from source to target.
	 */
	@GuardedBy("sfg")
	public boolean canGoTo(StateVertix source, StateVertix target) {
		synchronized (sfg) {
			return sfg.containsEdge(source, target) || sfg.containsEdge(target, source);
		}
	}

	/**
	 * Convenience method to find the Dijkstra shortest path between two states on the graph.
	 * 
	 * @param start
	 *            the start state.
	 * @param end
	 *            the end state.
	 * @return a list of shortest path of clickables from the state to the end
	 */
	public List<Eventable> getShortestPath(StateVertix start, StateVertix end) {
		return DijkstraShortestPath.findPathBetween(sfg, start, end);
	}

	/**
	 * Return all the states in the StateFlowGraph.
	 * 
	 * @return all the states on the graph.
	 */
	public Set<StateVertix> getAllStates() {
		return sfg.vertexSet();
	}

	/**
	 * Return all the edges in the StateFlowGraph.
	 * 
	 * @return a Set of all edges in the StateFlowGraph
	 */
	public Set<Eventable> getAllEdges() {
		return sfg.edgeSet();
	}

	/**
	 * Retrieve the copy of a state from the StateFlowGraph for a given StateVertix. Basically it
	 * performs v.equals(u).
	 * 
	 * @param state
	 *            the StateVertix to search
	 * @return the copy of the StateVertix in the StateFlowGraph where v.equals(u)
	 */
	private StateVertix getStateInGraph(StateVertix state) {
		Set<StateVertix> states = getAllStates();

		for (StateVertix st : states) {
			if (state.equals(st)) {
				return st;
			}
		}

		return null;
	}
	
	/**
	 * @return Dom string average size (byte).
	 */
	public int getMeanStateStringSize() {
		final Mean mean = new Mean();

		for (StateVertix state : getAllStates()) {
			mean.increment(state.getDomSize());
		}

		return (int) mean.getResult();
	}

	/**
	 * @return the state-flow graph.
	 */
	public DirectedGraph<StateVertix, Eventable> getSfg() {
		return sfg;
	}

	/**
	 * @param state
	 *            The starting state.
	 * @return A list of the deepest states (states with no outgoing edges).
	 */
	public List<StateVertix> getDeepStates(StateVertix state) {
		final Set<String> visitedStates = new HashSet<String>();
		final List<StateVertix> deepStates = new ArrayList<StateVertix>();

		traverse(visitedStates, deepStates, state);

		return deepStates;
	}

	private void traverse(Set<String> visitedStates, List<StateVertix> deepStates,
	        StateVertix state) {
		visitedStates.add(state.getName());

		Set<StateVertix> outgoingSet = getOutgoingStates(state);

		if ((outgoingSet == null) || outgoingSet.isEmpty()) {
			deepStates.add(state);
		} else {
			if (cyclic(visitedStates, outgoingSet)) {
				deepStates.add(state);
			} else {
				for (StateVertix st : outgoingSet) {
					if (!visitedStates.contains(st.getName())) {
						traverse(visitedStates, deepStates, st);
					}
				}
			}
		}
	}

	private boolean cyclic(Set<String> visitedStates, Set<StateVertix> outgoingSet) {
		int i = 0;

		for (StateVertix state : outgoingSet) {
			if (visitedStates.contains(state.getName())) {
				i++;
			}
		}

		return i == outgoingSet.size();
	}

	/**
	 * This method returns all possible paths from the index state using the Kshortest paths.
	 * 
	 * @param index
	 *            the initial state.
	 * @return a list of GraphPath lists.
	 */
	public List<List<GraphPath<StateVertix, Eventable>>> getAllPossiblePaths(StateVertix index) {
		final List<List<GraphPath<StateVertix, Eventable>>> results =
		        new ArrayList<List<GraphPath<StateVertix, Eventable>>>();

		final KShortestPaths<StateVertix, Eventable> kPaths =
		        new KShortestPaths<StateVertix, Eventable>(this.sfg, index, Integer.MAX_VALUE);
		// System.out.println(sfg.toString());

		for (StateVertix state : getDeepStates(index)) {
			// System.out.println("Deep State: " + state.getName());

			try {
				List<GraphPath<StateVertix, Eventable>> paths = kPaths.getPaths(state);
				results.add(paths);
			} catch (Exception e) {
				// TODO Stefan; which Exception is catched here???Can this be removed?
				LOGGER.error("Error with " + state.toString(), e);
			}

		}

		return results;
	}

	/**
	 * Return the name of the (new)State. By using the AtomicInteger the stateCounter is thread-safe
	 * 
	 * @return State name the name of the state
	 */
	public String getNewStateName() {
		stateCounter.getAndIncrement();
		String state = makeStateName(stateCounter.get(), false);
		return state;
	}

	/**
	 * Make a new state name given its id. Separated to get a central point when changing the names
	 * of states. The automatic state names start with "state" and guided ones with "guide".
	 * 
	 * @param id
	 *            the id where this name needs to be for.
	 * @return the String containing the new name.
	 */
	private String makeStateName(int id, boolean guided) {

		if (guided) {
			return "guided" + id;
		}

		return "state" + id;
	}
	
	
	
	
	/**
	 * @author aminmf
	 * Setting state DOM diversity for a state
	 * This is done by calling getDomDiversity which computes the tree edit distance
	 */
	private void setDOMDiversity(StateVertix stateVertix) {
		double DD;
		synchronized(statesDomDiversity){
			for (StateVertix s: sfg.vertexSet()){
				DD = getDomDiversity(s, stateVertix);
				//LOGGER.info("DD of states " + s + " and " + stateVertix + " is " + DD);
				statesDomDiversity.put(s.toString() + "," + stateVertix.toString(), DD);
				statesDomDiversity.put(stateVertix.toString() + "," + s.toString(), DD);
			}
		}
	}

	public double getDomDiversity(StateVertix s1, StateVertix s2) {
		double DD = 0.0;
		RTED_InfoTree_Opt rted;
		double ted;
		
		rted = new RTED_InfoTree_Opt(1, 1, 1);
		// compute tree edit distance
		rted.init(s1.getDomTree(), s2.getDomTree());

		//long time1 = (new Date()).getTime();

		int maxSize = Math.max(s1.getDomTree().getNodeCount(), s2.getDomTree().getNodeCount());
		
		rted.computeOptimalStrategy();
		ted = rted.nonNormalizedTreeDist();
		ted /= (double)maxSize;

		//long time2 = (new Date()).getTime();
		//LOGGER.info("normalized distance is:             " + ted);
		//LOGGER.info("runtime:              " + ((time2 - time1) / 1000.0));
		
		DD = ted;
		return DD;
	}

	public double getMinDOMDiversity(StateVertix stateVertix) {
		double DD = 0.0, minDD = 1.0;
		if (sfg.vertexSet().size()!=0)
			for (StateVertix s: sfg.vertexSet()){
				if (!stateVertix.equals(s) && statesDomDiversity.containsKey(stateVertix.toString() + "," + s.toString())){
					DD = statesDomDiversity.get(stateVertix.toString() + "," + s.toString());
					//LOGGER.info("DD of state " + s + " and " + stateVertix + " is " + DD);
					if (DD < minDD) minDD = DD;
				}
			}
		return minDD;
	}
	
	
	
	
	/**
	 * @author aminmf
	 * Setting coverage increase for a state using the latestCoverageIncrease
	 */
	private void setCoverageIncrease(StateVertix stateVertix) {
		synchronized(statesCoverageIncrease){
				LOGGER.info("CoverageIncrease for states " + stateVertix.getName() + " is " + latestCoverageIncrease);
				statesCoverageIncrease.put(stateVertix.toString(), latestCoverageIncrease);
		}
	}

	public double getCoverageIncrease(StateVertix stateVertix) {
		double cov = 0.0;

		synchronized(statesCoverageIncrease){
			try{
				cov = statesCoverageIncrease.get(stateVertix.toString());	
				LOGGER.info("**** CoverageIncrease for states " + stateVertix.getName() + " is " + cov);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return cov;
	}


	
	
	/**
	 * @author aminmf
	 * Calculate state path diversity
	 */
	private void setPathDiversity(StateVertix stateVertix) {
		double PD;
		synchronized(statesPathDiversity){
			for (StateVertix s: sfg.vertexSet()){
				PD = getPathDiversity(s, stateVertix);
				//LOGGER.info("PD of states " + s + " and " + stateVertix + " is " + PD);
				statesPathDiversity.put(s.toString() + "," + stateVertix.toString(), PD);
				statesPathDiversity.put(stateVertix.toString() + "," + s.toString(), PD);
			}
		}
	}
	
	public double getMinPathDiversity(StateVertix stateVertix) {
		double PD = 0.0, minPD = 1.0;
		if (sfg.vertexSet().size()!=0)
			for (StateVertix s: sfg.vertexSet()){
				if (!stateVertix.equals(s) && statesPathDiversity.containsKey(stateVertix.toString() + "," + s.toString())){
					PD = statesPathDiversity.get(stateVertix.toString() + "," + s.toString());
					//LOGGER.info("DD of state " + s + " and " + stateVertix + " is " + DD);
					if (PD < minPD) minPD = PD;
				}
			}
		return minPD;
	}	
	
	/**
	 * @author aminmf
	 * Calculate state path diversity
	 */
	private Stack<StateVertix> path  = new Stack<StateVertix>();   // the current path
	private Set<StateVertix> onPath = new HashSet<StateVertix>();  // the set of vertices on the path
	private ArrayList<ArrayList<String>> allPaths1  = new ArrayList<ArrayList<String>>();
	private ArrayList<ArrayList<String>> allPaths2  = new ArrayList<ArrayList<String>>();
	
	public double getPathDiversity(StateVertix s1, StateVertix s2) {
		double sim = 0.0;
		double maxSim = 0.0;
		// should be cleared each time
		allPaths1.clear();
		allPaths2.clear();
		
		getAllPaths(s1, allPaths1);
		getAllPaths(s2, allPaths2);
		
		// calculate intersection of pair-wise event paths
		for (ArrayList<String> eventPath1 : allPaths1){
			for (ArrayList<String> eventPath2 : allPaths2){
				int minLength = Math.min(eventPath1.size() , eventPath2.size());
				int intersection = 0;
				for (int i=1; i<minLength ; i++) // should not consider index state in path intersection computation
					if (eventPath1.get(i)==eventPath2.get(i))
						intersection++;
					else
						break;
				
				sim = 2 * (double) intersection / (double)(eventPath1.size() + eventPath2.size());
				if (sim > maxSim)
					maxSim = sim;
			}
		}
		return 1 - maxSim;
	}
	
	//	generate all paths from "index" state to target state "t"
	public void getAllPaths(StateVertix t, ArrayList<ArrayList<String>> allPaths){
		// find index state
		for (StateVertix s: sfg.vertexSet())
			if (s.getName().equals("index")){
				enumerateAllPaths(s, t, allPaths); // using DFS
				break;
			}
	}
	
	public void enumerateAllPaths(StateVertix v, StateVertix t, ArrayList<ArrayList<String>> allPaths) {
		// add node v to current path from s
		path.push(v);
		onPath.add(v);
		// found path from s to t - currently prints in reverse order because of stack
		if (v.getName().equals(t.getName())){ 
			ArrayList<String> newPath = new ArrayList<String>(); // storing one discovered path from index to t
			newPath.clear();
			for (StateVertix s: path)
				newPath.add(s.getName());
			allPaths.add(newPath);
		}
		else { // consider all neighbors that would continue path with repeating a node
			for (StateVertix w : Graphs.neighborListOf(sfg, v)) {
				if (!onPath.contains(w)) 
					enumerateAllPaths(w, t, allPaths);
			}
		}
		// done exploring from v, so remove from path
		path.pop();
		onPath.remove(v);
	}

	
	
	
	
	/**
	 * @author aminmf
	 * Event productivity ratio calculation
	 */
	
	public void updateEventProductivity(Eventable eventable, StateVertix resultingState){
		// increase count of the eventable
		synchronized(eventCount){
			int eventCounter = 0;
			if (eventCount.containsKey(eventable.getElement())){ 
				eventCounter = eventCount.get(eventable.getElement());
				//System.out.println(eventable.getElement() + " was executed " + eventCounter + " times before");			
			}
			eventCount.put(eventable.getElement().toString(), eventCounter + 1);
		}		
		// increase eventScore of the eventable
		synchronized(eventScore){
			double minDD = 1.0, score =0.0;
			if (resultingState == null) // this happens when clone state is detected
				minDD = 0.0;
			else{
				minDD = 1;//  0-1 version
				//minDD = getMinDOMDiversity(resultingState);
			}
			if (eventScore.containsKey(eventable.getElement()))
				score = eventScore.get(eventable.getElement());
			
			//System.out.println(eventable.getElement() + " was added to eventScore with score" + score + " + " + minDD);
			
			eventScore.put(eventable.getElement().toString(), score + minDD);
		}
	}

	public double getEventProductivity(CandidateElement c){
		// if e has never been executed
		String eventString = new Element(c.getElement()).toString();
		if (!eventScore.containsKey(eventString)){
			//System.out.println(eventString + " was never executed before so productivity ratio is 1");
			return 1.0;
		}
		double EP = eventScore.get(eventString) /(double) eventCount.get(eventString);
		//System.out.println(eventString + " has event productivity ratio of " + EP + "!!!!!!");
		return EP;
	}

	

	/**
	 * Amin: used for reporting
	 * @return
	 */
	public double getFinalDOMDiv(){
		double res = 0.0;
		if (done) 
			return res;
		for (StateVertix s1: sfg.vertexSet()){
			for (StateVertix s2: sfg.vertexSet()){
				res += getDomDiversity(s1, s2);
			}
		}
		res /= 2;   // to cancel out duplicate summation of DD(i,j) and DD(j,i)
		res /=  (sfg.vertexSet().size() * (sfg.vertexSet().size()-1));
		return res;
	}



	/**
	 * Amin: used for reporting
	 * @return
	 */
	public double getFinalPathDiv(){
		double res = 0.0,temp=0.0;
		if (done)
			return res;
		for (StateVertix s1: sfg.vertexSet()){
			for (StateVertix s2: sfg.vertexSet()){
				temp = getPathDiversity(s1, s2);
				res += temp;
				System.out.println("Temp is: " + temp);
			}
		}
		res /= 2;   // to cancel out duplicate summation of DD(i,j) and DD(j,i)
		res /=  (sfg.vertexSet().size() * (sfg.vertexSet().size()-1));
		done = true;
		return res;
	}
	
	
	/**
	 * Estimator
	 */
	public String updateEstimator(){
		estimator.updateEstimator(getAllStates().size(), getAllEdges().size());
		System.out.println(estimator);
		return estimator.toString();
	}
	

}
