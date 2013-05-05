package codesmells;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.*;
import org.w3c.dom.Node;

/**
 * Main JSNose smell detection method
 * 
 * @author Aimin Milani Fard
 */
public class SmellDetector {

	// JSNose parameters for smell detection
	private static final int MAX_METHID_LENGTH = 50;			// function/method length
	private static final int MAX_NUMBER_OF_PARAMETERS = 5;		// function parameter
	private static final int MAX_LENGTH_OF_PROTOTYPE = 3;		// prototype chain
	private static final int MAX_LENGTH_OF_MESSAGE_CHAIN = 3;	// message chain
	private static final int MAX_NUMBER_OF_SWITCHCASE = 3;		// switch
	private static final int MAX_LENGTH_OF_SCOPE_CHAIN = 3;		// closure
	private static final double BASE_CLASS_USAGE_RATIO = 0.33;	// refused bequest
	public static final int MIN_OBJECT_PROPERTIES = 3;			// lazy object
	public static final int MAX_OBJECT_PROPERTIES = 20;			// large object
	private static final int MAX_OBJECT_LOC = 750;				// large object
	

	private AstNode ASTNode;

	private static ArrayList<JavaScriptObjectInfo> jsObjects = new ArrayList<JavaScriptObjectInfo>();
	
	private static ArrayList<FunctionInfo> jsFunctions = new ArrayList<FunctionInfo>();


	private String candidateObjectName = "";		// this will be set to name of any variable and if detected as object will be added to jsObjects
	private boolean nextNameIsProperty = false;		// this is to distinguish properties of an object from other var/names
	private boolean nextNameIsPrototype = false;	// this is to distinguish prototype of an object from other var/names
	private boolean nextNameIsObject = false;		// this is to distinguish properties from an object such as for x in x.prototype = y;
	
	private boolean callBackFound = false;
	
	private int currentObjectNodeDepth = 0;	// This is node.depth() for the current object. A property would be added if its node.depth() is higher than currentObjectNodeDepth
	private int currentObjectIndex = 0;		// This is the index of current object in jsObjects list (used to add properties/prototype)
	private String currentIdentifier = "";	// this is to keep the latest identifier as it may be detected as an object following the pattern x=Object.create
	private String currentPrototype = "";	// this is to keep the prototype x found at Object.create(x)

	private int consecutivePropertyGet = 0;	// This is to store number of consecutive getting of property used to detect long message chain 
	private int lastMessageChain = 0;		// This is to store last message chain using consecutivePropertyGet 
	private boolean ignoreDepthChange = false;		// This is also used to decide for a.b.c pattern that c is a property of b not a separate identifier 
	private static HashSet<SmellLocation> longMessageFound = new HashSet<SmellLocation>();	// keeping line number where a long message occurred
	
	private boolean LHS = false;			// This is to decide if the ASTNode is at the left hand-side of an assignment 
	private int assignmentNodeDepth = 0;	// This is to store ASTNode depth of assignment to be used for detecting LHS value 
	private boolean assignmentLHSVisited = false; 
	
	
	private boolean CatchClause = false;	// To detect empty Catch Clauses
	private static HashSet<SmellLocation> emptyCatchFound = new HashSet<SmellLocation>();	// keeping line number where an empty catch occurred

	private static HashSet<SmellLocation> longMethodFound = new HashSet<SmellLocation>();	// keeping line number where a long method is defined

	private static HashSet<SmellLocation> longParameterListFound = new HashSet<SmellLocation>();	// keeping line number where a long parameter list is found

	private static HashSet<SmellLocation> switchFound = new HashSet<SmellLocation>();	// keeping line number where a switch statement is found
	
	private int lastFunctionDepth = 0;
	private int scopeChainLength = 0;
	private static HashSet<SmellLocation> closureSmellLocation = new HashSet<SmellLocation>();	// keeping line number of the inner function of a deep closure
	private static HashSet<SmellLocation> nestedCallBackFound = new HashSet<SmellLocation>();	// keeping line number of the inner function of a deep closure

	private static int inlineJavaScriptLines = 0;	// keep the count of js code in <script> tags
	private static HashSet<String> inlineJavaScriptScopeName = new HashSet<String>();	// keeping scope name (js file name) where inline JavaScript is detected
	private static int NumberOfInTagJS1 = 0; 
	private static HashSet<String> jsInTagFound = new HashSet<String>();			// keeping occurrences of unique js in html tags
	private static HashSet<SmellLocation> CSSinJS = new HashSet<SmellLocation>();	// keeping line number where CSS is used in JS
		
	
	private static HashSet<String> globals = new HashSet<String>();	// keeping global variables
	
	public static void setGlobals(HashSet<String> globals) {
		for (String g: globals)
			SmellDetector.globals.add(g);
	}

	
	private static HashSet<SmellLocation> refusedBequestObjLocation = new HashSet<SmellLocation>();	// keeping objects which refuse bequests

	private static HashSet<SmellLocation> lazyObjectsLocation = new HashSet<SmellLocation>();	// keeping lazy objects

	private static HashSet<SmellLocation> largeObjectsLocation = new HashSet<SmellLocation>();	// keeping large objects
	
	private static HashSet<SmellLocation> longPrototypeChainObjLocation = new HashSet<SmellLocation>();	// keeping objects with long prototype chain
	
	// list of objects to ignore in reporting large/lazy object and refused bequest
	private static ArrayList<String> objectsToIgnore = new ArrayList<String>();	
	
	
	private boolean checkForUnreachable = false;
	private int levelToCheckForReachability = 0;
	private static HashSet<SmellLocation> unReachable = new HashSet<SmellLocation>();	// keeping unreachable code line number

	
	/**
	 * This list is for keeping name of candidate javascript objects found in the code
	 * they are called candidate since some my not be actual objects
	 */
	private static List<String> candidateJSObjectList = new ArrayList<String>();

	public static List<String> getcandidateJSObjectList(){
		return candidateJSObjectList;
	}
	
	
	
	public SmellDetector() {
		ASTNode = null;
		// ignore these objects when reporting object-based smells
		objectsToIgnore.add("window");
		objectsToIgnore.add("document");
		objectsToIgnore.add("top");
		objectsToIgnore.add("navigator");
		objectsToIgnore.add("Math");
		objectsToIgnore.add("location");
		objectsToIgnore.add("InstallTrigger");
		objectsToIgnore.add("fxdriver_id");
		objectsToIgnore.add("__fxdriver_unwrapped");
		objectsToIgnore.add("jQuery");
		objectsToIgnore.add("$");
		objectsToIgnore.add("setInterval");
		objectsToIgnore.add("setTimeout");
	}
	

	public void SetASTNode(AstNode node) {
		ASTNode = node;
	}

	private static String jsFileName;
	
	public static void setJSName(String jsName) {
		SmellDetector.jsFileName = jsName;
	}
	
	
	/**
	 * Showing list of smells when all AST nodes were visited. The method is static to be called in JSModifyProxyPlugin.modifyJS()
	 */
	public static void generateReport(){

		// TODO: write in text file
		System.out.println("***************************************");
		System.out.println("********** CODE SMELL REPORT **********");
		System.out.println("***************************************");

		analyseObjecsList();

		System.out.println("********** CLOSURE SMELL **********");
		reportSmell(closureSmellLocation);

		
		System.out.println("********** COUPLING JS/HTML **********");
		
		System.out.println("Total number of JavaScript in HTML tags: " + jsInTagFound.size());
		
		for (String jTag: jsInTagFound)
			System.out.println(jTag);
		
		System.out.println("Occurance of CSS in JavaScript");
		reportSmell(CSSinJS);
		
		//System.out.println("Total number of JavaScript lines in HTML: " + inlineJavaScriptLines);
		//for (String sn: inlineJavaScriptScopeName)
		//	System.out.println("Scope having the inline JavaScript: " + sn);

		
		System.out.println("********** EMPTY CATCH **********");
		reportSmell(emptyCatchFound);

		// because globals are extracted at runtime, they are not available in the first execution of this part of code
		if (globals.size() > 0){
			System.out.println("********** EXCESSIVE GLOBAL VARIABLES **********");
			System.out.println("Number of global variables: " + globals.size());
			System.out.println("List of  global variables: " + globals);
		}
		
		System.out.println("********** LARGE OBJECT **********");
		reportSmell(largeObjectsLocation);		

		System.out.println("********** LAZY OBJECT **********");
		reportSmell(lazyObjectsLocation);
		
		System.out.println("********** LONG MESSAGE **********");
		reportSmell(longMessageFound);

		System.out.println("********** LONG METHOD/FUNCTION **********");
		reportSmell(longMethodFound);

		System.out.println("********** LONG PARAMETER LIST **********");
		reportSmell(longParameterListFound);

		//System.out.println("********** LONG PROTOTYPE CHAIN **********");
		//reportSmell(longPrototypeChainObjLocation);

		
		// More detection process for callback is to dynamically check if the type of a parameter is function
		System.out.println("********** NESTED CALLBACK **********");
		reportSmell(nestedCallBackFound);

		System.out.println("********** REFUSED BEQUEST **********");
		reportSmell(refusedBequestObjLocation);
		
		System.out.println("********** SWITCH STATEMENT **********");
		reportSmell(switchFound);
				

		System.out.println("********** UNREACHABLE CODE **********");
		reportSmell(unReachable);
		
		
		//System.out.println("********** OBJECT LIST **********");
		//for (JavaScriptObjectInfo o: jsObjects)
		//	System.out.println(o);
		
		//System.out.println("FUNCTIONS ARE: ");
		//for (int i=0;i<jsFunctions.size();i++)
		//	System.out.println(jsFunctions.get(i).getName());
		
	}
	
	public static void reportSmell(HashSet<SmellLocation> smell){
		System.out.println("Number of occurance: " + smell.size());
		for (SmellLocation l:smell)
			System.out.println("Item: " + l.getSmellyItemName() + " in JS file: " + l.getJsFile() +" at line number: " + l.getLineNumber());
	}	
	

	
	
	
	/**
	 * Analysing jsObjects list to calculate used/unused inherited properties 
	 * The method is static to be used by printObject()
	 */
	public static void analyseObjecsList() {
		
		String prototype = "";
		
		//	usedInheritedPropetries = intersection of ownPropetries and inheritedPropetries 
		//	usedInheritedPropetries = inheritedPropetries - ownPropetries
		ArrayList<String> ownPropetries = new ArrayList<String>();	// only own
		ArrayList<String> usedPropetries = new ArrayList<String>();	// both own and inherited
		ArrayList<String> inheritedPropetries = new ArrayList<String>();		// ownPropetries of the prototype (if has one)
		ArrayList<String> usedInheritedPropetries = new ArrayList<String>();	// Inherited properties used or overwritten
		ArrayList<String> notUsedInheritedPropetries = new ArrayList<String>();	// Inherited properties not used or overwritten

		ArrayList<String> delegatedPropetries = new ArrayList<String>();		// Delegated properties which are defined some where in the prototype chain 
		ArrayList<JavaScriptObjectInfo> prototypeChain = new ArrayList<JavaScriptObjectInfo>();				// Storing the prototype chain of an object 

		SmellLocation sl;
		
		for (JavaScriptObjectInfo jso : jsObjects){
			
			//System.out.println(jso);
			
			usedInheritedPropetries.clear();
			notUsedInheritedPropetries.clear();
			delegatedPropetries.clear();
			prototypeChain.clear();
			
			ownPropetries = jso.getOwnPropetries();
			//System.out.println("ownPropetries of :" + jso.getName() + " is: " + ownPropetries);
			
			
			if (!objectsToIgnore.contains(jso.getName()) && !jso.getPrototype().equals("Date") && !jso.getPrototype().equals("XMLHttpRequest")){
				/**
				 * Detecting lazy object
				 */

				if (ownPropetries.size() < MIN_OBJECT_PROPERTIES){
					sl = new SmellLocation(jso.getName(),jso.getJsFileName(),jso.getLineNumber());
					System.out.println(jso.getName() + " is a lazy object because ownPropetries is " + ownPropetries);
					lazyObjectsLocation.add(sl);
				}


				/**
				 * Detecting large object
				 */
				int LOC = 0; // object methods lines of code
				// counting total LOC of its methods
				for (int i=0; i<ownPropetries.size(); i++){
					for (int j=0; j<jsFunctions.size(); j++){
						if (ownPropetries.get(i).equals(jsFunctions.get(j).getName()))
							LOC += jsFunctions.get(j).getLinesOfCode();
					}
				}
				if (LOC >= MAX_OBJECT_LOC || ownPropetries.size() > MAX_OBJECT_PROPERTIES){
					sl = new SmellLocation(jso.getName(),jso.getJsFileName(),jso.getLineNumber());

					System.out.println(jso.getName() + " is a large object because LOC is " + LOC + " and ownPropetries.size() is " + ownPropetries.size());
					largeObjectsLocation.add(sl);
				}

			}


			
			usedPropetries = jso.getUsedPropetries();
			//System.out.println("usedPropetries of :" + jso.getName() + " is: " + usedPropetries);
			
			//detecting not-own but used properties
			for (String used: usedPropetries)
				if (!ownPropetries.contains(used)){
					//System.out.println("property: " + used + " was delegated to prototype chain");
					delegatedPropetries.add(used);
				}

			prototype = jso.getPrototype();
			if (prototype!=""){

				//System.out.println("prototype of :" + jso.getName() + " is: " + prototype);

				for (JavaScriptObjectInfo proto : jsObjects)
					if (proto.getName().equals(prototype)){
						
						prototypeChain.add(proto);
						
						inheritedPropetries = proto.getOwnPropetries();

						jso.setInheritedPropetries(inheritedPropetries);
						//System.out.println("inheritedPropetries of :" + jso.getName() + " is: " + jso.getInheritedPropetries());


						for (String prop : inheritedPropetries){
							if (ownPropetries.contains(prop))	// finding used/overrode inherited properties
								usedInheritedPropetries.add(prop);
							else
								notUsedInheritedPropetries.add(prop);
						}

						jso.setUsedInheritedPropetries(usedInheritedPropetries);
						jso.setNotUsedInheritedPropetries(notUsedInheritedPropetries);

						//System.out.println("usedInheritedPropetries of :" + jso.getName() + " is: " + jso.getUsedInheritedPropetries());

						//System.out.println("notUsedInheritedPropetries of :" + jso.getName() + " is: " + jso.getNotUsedInheritedPropetries());



						/**
						 * Detecting refused bequest
						 */
						if (!objectsToIgnore.contains(jso.getName())){
							if ( (double)usedInheritedPropetries.size() / (double)inheritedPropetries.size() < BASE_CLASS_USAGE_RATIO){
								//System.out.println("Detected refused bequest for object: " + jso.getName());
								sl = new SmellLocation(jso.getName(),jso.getJsFileName(),jso.getLineNumber());
								refusedBequestObjLocation.add(sl);
							}
						}


						// detecting prototype-chain
						boolean prototypeFound = false;
						prototype = proto.getPrototype();
						int chainLength = 0;
						
						if (!prototype.equals(proto.getName())){

							//System.out.println("prototype of :" + proto.getName() + " is: " + prototype);

							while(prototype != ""){
								prototypeFound = false;
								for (JavaScriptObjectInfo o : jsObjects)
									if (o.getName().equals(prototype)){
										prototype = o.getPrototype();
										prototypeChain.add(o);
										prototypeFound = true;
										break;
									}
								if (prototypeFound == false)
									break;
								chainLength++;
								if (chainLength == 5){
									break;
								}
							}

						}

						//System.out.println("prototypeChain is: " + prototypeChain);
						if (prototypeChain.size() >= MAX_LENGTH_OF_PROTOTYPE){
							//System.out.println("Long prototype chain found for object: " + jso.getName() + " defined at line: " + jso.getLineNumber());
							sl = new SmellLocation(jso.getName(),jso.getJsFileName(),jso.getLineNumber());
							longPrototypeChainObjLocation.add(sl);
						}
						

						// detecting delegation in prototype-chain
						for (String delProp : delegatedPropetries){
							boolean found = false;
							for (JavaScriptObjectInfo o : prototypeChain){
								inheritedPropetries = o.getOwnPropetries();
								if (inheritedPropetries.contains(delProp)){	// finding used/overrode inherited properties
									//System.out.println("delegated property: " + delProp + " was found in object: " + o.getName());
									found = true;
									break;
								}
							}
							//if (found == false)
								//System.out.println("Could not find delegated property: " + delProp + " in prototypeChain");
						}

						break;

					}

			}

			//System.out.println();
			
		}
	}

	
	
	/**
	 * Analysing ASTNode for code smells.
	 */
	public void analyseAstNode() {
	
		String ASTNodeName = ASTNode.shortName();
		int type = ASTNode.getType();
		int ASTDepth = ASTNode.depth();
		
		//System.out.println("node.shortName() : " + ASTNodeName);
		//System.out.println("node.depth() : " + ASTDepth);
		//System.out.println("node.getLineno() : " + (ASTNode.getLineno()+1));
		
		checkLongMessageChain();   // also used to detect message chain used in object recognition

		// check if we are in the up the currentObjectNodeDepth
		if (ASTDepth < currentObjectNodeDepth && lastMessageChain==1 && ignoreDepthChange==false){  // dealing with a.b.c = ... patterns  
			nextNameIsProperty = false;
			//nextNameIsObject = true;
			//System.out.println("analyseAstNode(): Level changed! nextNameIsObject");
			//System.out.println("analyseAstNode(): Level changed! nextName is not property anymore");
		}
		
		// check if we are in LHS of the current assignment, used to check if a property is defined and not just used
		if (ASTDepth==assignmentNodeDepth+1){
			if (assignmentLHSVisited == false){
				assignmentLHSVisited = true;
			}else
				LHS = false;
		}

		
//		if (ASTNodeName.equals("Name")){
//			//System.out.println(ASTNode.debugPrint());
//
//			for (Symbol s: ASTNode.getAstRoot().getSymbols()){
//				int sType = s.getDeclType();
//			    if (sType == Token.LP || sType == Token.VAR || sType == Token.LET || sType == Token.CONST){
//			    	System.out.println("s.getName() : " + s.getName());
//			    }
//			}
//			System.out.println();
//		}
//		else if (ASTNodeName.equals("FunctionNode")){
//			FunctionNode f = (FunctionNode) ASTNode;
//			for (Symbol s: f.getSymbols()){
//				int sType = s.getDeclType();
//			    if (sType == Token.LP || sType == Token.VAR || sType == Token.LET || sType == Token.CONST){
//			    	System.out.println("s.getName() : " + s.getName());
//			    }
//			}
//			
//			System.out.println(f.getSymbolTable());
//			System.out.println(f.getSymbols());
//
//		}else
//			return;
        
		
		
		
		// check if the new statement is after return, break, continue, or throw AND at the same level
		if (checkForUnreachable==true){
			if (ASTNode.depth() == levelToCheckForReachability){
				//System.out.println("Unreachable code at line: " + (ASTNode.getLineno()+1));
				SmellLocation sl = new SmellLocation("Unreachable code",jsFileName,(ASTNode.getLineno()+1));
				unReachable.add(sl);
			}
			checkForUnreachable = false;
		}
		
		
		
		if (ASTNodeName.equals("Name"))
			analyseNameNode();
		else if (ASTNodeName.equals("VariableDeclaration"))
			analyseVariable();
		else if (ASTNodeName.equals("ObjectLiteral"))
			analyseObjectLiteralNode();
		else if (ASTNodeName.equals("ObjectProperty"))
			analyseObjectPropertyNode();
		else if (ASTNodeName.equals("FunctionNode"))
			analyseFunctionNode();
		else if (ASTNodeName.equals("PropertyGet"))  // this is for inner function defined properties such as this.name = ...
			analysePropertyGetNode();
		else if (ASTNodeName.equals("NewExpression"))
			analyseNewExpressionNode();
		else if (ASTNodeName.equals("FunctionCall"))
			analyseFunctionCallNode();
		else if (ASTNodeName.equals("Assignment"))
			analyseAssignmentNode();
		else if (ASTNodeName.equals("CatchClause"))
			analyseCatchClause();
		else if (ASTNodeName.equals("ReturnStatement") || ASTNodeName.equals("BreakStatement") || ASTNodeName.equals("ContinueStatement") || ASTNodeName.equals("ThrowStatement"))
			analyseRechability();
		else if (ASTNodeName.equals("Block"))
			analyseBlock();		
		else if (type == Token.SWITCH)
			isSwitchSmell();
		else if (type == Token.THIS)
			thisInClosure();
		

		//System.out.println();

		//System.out.println("node.toSource() : " + node.toSource());
		//System.out.println("node.getType() : " + node.getType());
		//System.out.println("node.getAstRoot() : " + node.getAstRoot());
		//System.out.println("node.debugPrint() : " + node.debugPrint());
	}


	// checking if "this" is used in closure
	private void thisInClosure() {
		if (scopeChainLength > 1){
			SmellLocation sl = new SmellLocation("this in closure", jsFileName, ASTNode.getLineno()+1);
			closureSmellLocation.add(sl);
		}

	}



	// check if next statement is unreachable
	private void analyseRechability() {
		checkForUnreachable = true;
		levelToCheckForReachability = ASTNode.depth();
	}

	
	// detecting local variable
	private void analyseVariable() {
		//nextIsLocal = true;		
	}

	/**
	 * Empty catch clause detection
	 */
	private void analyseCatchClause() {
		CatchClause = true;		
	}

	private void analyseBlock() {
		if (CatchClause==true){
			if (ASTNode.hasChildren()==false){
				//System.out.println("Empty catch clause at line: " + (ASTNode.getLineno()+1));
				SmellLocation sl = new SmellLocation("empty catch",jsFileName,(ASTNode.getLineno()+1));
				emptyCatchFound.add(sl);
			}
			CatchClause = false;
		}
	}


	/**
	 * Deciding if an expression is a LHS
	 * Used to distinguish ownProperties and usedProperties
	 */
	
	private void analyseAssignmentNode() {
		assignmentNodeDepth = ASTNode.depth();
		assignmentLHSVisited = false;
		LHS = true;
	}

	/**
	 * checking if name is the name of an object, function, property, etc.
	 */
	public void analyseNameNode() {		

		// setting candidateObjectName and currentIdentifier
		candidateObjectName = ((Name)ASTNode).getIdentifier();
		
		// this is to keep track during dynamic execution
		if (!candidateJSObjectList.contains(candidateObjectName)){
			candidateJSObjectList.add(candidateObjectName);
			//System.out.println("objName: " + objName);
		}
		
		//System.out.println("idenfifier: " + candidateObjectName );
		currentIdentifier = candidateObjectName;	// this is to be used in method analyseFunctionCallNode() if a pattern of Object.create(x) was detected where x is the currentPrototype
	
		
		
		

		/*
		  Adding a property of the object or if it is the keyword "prototype" or "__proto__" setting the prototype to Object.prototype
		 */
		if (nextNameIsProperty == true){		// check if next name is a property name, default is false

			//System.out.println("NameIsProperty=true for the name: " + ((Name)ASTNode).getIdentifier());

			if (((Name)ASTNode).getIdentifier().equals("prototype") || ((Name)ASTNode).getIdentifier().equals("__proto__")){	// check if prototype object is declared (if a pattern of x.prototype found)
				/**
				 * check if it is "Object.prototype" and not "x.prototype" = y
				 */
				//System.out.println("should find a prototype for current object: " + jsObjects.get(currentObjectIndex).getName());

				nextNameIsProperty = false;

			}else{	

				// checking css in javascript 
				if (((Name)ASTNode).getIdentifier().equals("style")){
					SmellLocation sl = new SmellLocation("CSS in JavaScript", jsFileName,(ASTNode.getLineno()+1));
					System.out.println("CSSinJS : at line " + (ASTNode.getLineno()+1) + " of file: " + jsFileName);
					CSSinJS.add(sl);
				}else if (jsObjects.size()>0){

					/*
    			  Adding a property to the current object
					 */
					if (!((Name)ASTNode).getIdentifier().equals(jsObjects.get(currentObjectIndex).getName())){ // ignoring to add the function name as a property of the object
						//System.out.println("property found: " + ((Name)ASTNode).getIdentifier() + " for object: " + jsObjects.get(currentObjectIndex).getName());

						if (LHS==true){
							//System.out.println("THIS IS AN OWN PROPERTY!");
							jsObjects.get(currentObjectIndex).addOwnProperty(((Name)ASTNode).getIdentifier());
							jsObjects.get(currentObjectIndex).addUsedProperty(((Name)ASTNode).getIdentifier());
						}else{
							//System.out.println("THIS IS A USED PROPERTY!");
							jsObjects.get(currentObjectIndex).addUsedProperty(((Name)ASTNode).getIdentifier());
						}


						//System.out.println("lastMessageChain is :" + lastMessageChain);
						if (lastMessageChain==1){ // normal case (no nested object.object.object...)
							nextNameIsProperty = true;
							ignoreDepthChange = false;
							//System.out.println("ignoreDepthChange = false");
						}
						else{ // dealing with a.b.c = ... pattern

							//System.out.println("lastMessageChain is : " + lastMessageChain + " so " + ((Name)ASTNode).getIdentifier() + " should be a new object");

							candidateObjectName = ((Name)ASTNode).getIdentifier();
							JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
							newJSObj.setJsFileName(jsFileName);
							if (!objectExists(newJSObj)){		// add the new object if does not already exist
								jsObjects.add(newJSObj);
								currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
								currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
								//System.out.println("A new object is used: " + candidateObjectName);
							}
							//System.out.println("object in use: " + candidateObjectName);
							nextNameIsObject = false;
							//System.out.println("analyseNameNode(): nextNameIsProperty");
							nextNameIsProperty = true;  // to read next name as the property of this object such as x.propX. propX may also be prototype and should be avoided to add as property

							ignoreDepthChange = true;
							//System.out.println("ignoreDepthChange = true");

							lastMessageChain--;

							//System.out.println("lastMessageChain is :" + lastMessageChain);

						}
					}
				}
			}
		}


		/*
		  check if next name is a prototype object name, default is false
		 */
		if (nextNameIsPrototype == true){
			//System.out.println("prototype found: " + ((Name)ASTNode).getIdentifier() + " for object: " + jsObjects.get(currentObjectIndex).getName());
			jsObjects.get(currentObjectIndex).setPrototype(((Name)ASTNode).getIdentifier());
			nextNameIsPrototype = false;
		}
		
		
		if (nextNameIsObject == true){	// check if next name is an object name, default is false
			// either an already found object or a new object. Example is oldObj.newProperty or newObj.newProperty
			candidateObjectName = ((Name)ASTNode).getIdentifier();
			JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
			newJSObj.setJsFileName(jsFileName);
			if (!objectExists(newJSObj)){		// add the new object if does not already exist
				jsObjects.add(newJSObj);
				currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
				currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
				//System.out.println("A new object is used: " + candidateObjectName);
			}else{
				setCurrentObject(newJSObj);
			}

			//System.out.println("object in use: " + candidateObjectName);
			nextNameIsObject = false;
			//System.out.println("analyseNameNode(): nextNameIsProperty");
			nextNameIsProperty = true;  // to read next name as the property of this object such as x.propX. propX may also be prototype and should be avoided to add as property
		}

	}





	/**
	 * Extracting object literals in javaScript
	 */
	public void analyseObjectLiteralNode() {

		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
		newJSObj.setType("ObjectLiteral");
		newJSObj.setJsFileName(jsFileName);

		
		ObjectLiteral o = ( ObjectLiteral) ASTNode;
		//System.out.println("Found object literal: " + candidateObjectName);
		List<ObjectProperty> prop =  o.getElements();
		for (ObjectProperty op : prop){
			if (op.getLeft().shortName().equals("Name")){
				//System.out.println("op.getString(): " + ((Name)(op.getLeft())).getIdentifier()  );
				newJSObj.addOwnProperty(((Name)(op.getLeft())).getIdentifier());
			}
			else if (op.getLeft().shortName().equals("StringLiteral")){
				//System.out.println("op.getString(): " + ((StringLiteral)(op.getLeft())).getValue()  );
				newJSObj.addOwnProperty(((StringLiteral)(op.getLeft())).getValue());
			}
			else{
				System.out.println("UNKNOWN!!");
			}
		}
				
		jsObjects.add(newJSObj);
		currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
		currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
		//System.out.println("Object literal: " + candidateObjectName);
	}

	public void analyseObjectPropertyNode() {
		if (ASTNode.depth() > currentObjectNodeDepth){ // check depth of the prop with current object's depth
				//System.out.println("analyseObjectPropertyNode(): nextNameIsProperty");
				nextNameIsProperty = true;
		}
		//else
		//	nextNameIsProperty = false;
	}


	/**
	 * Extracting objects created by new keyword in javaScript
	 */
	public void analyseFunctionNode() {
	
		FunctionNode f = (FunctionNode) ASTNode;

		String fName = "";
		if (f.getFunctionName()!=null){
			fName = f.getFunctionName().getIdentifier();
		}
		
		int numOfParam = f.getParams().size();
		int lineNumber = ASTNode.getLineno()+1;
		int fLength = f.getEndLineno() - f.getLineno();
		int fDepth = ASTNode.depth();
		
		//System.out.println(f.debugPrint());

		// adding the function to the list of jsFunctions if does not already exist
		FunctionInfo newFunction = new FunctionInfo(fName, numOfParam, fLength, lineNumber);
		
		boolean functionExist = false;
		for (FunctionInfo jsfn : jsFunctions)
			if (jsfn.getName().equals(newFunction.getName())){
				functionExist = true;
				break;
			}
		if (functionExist == false)
			jsFunctions.add(newFunction);
				
		
		/**
		 * Detecting long method/function
		 */
		if (fLength > MAX_METHID_LENGTH){
			SmellLocation sl = new SmellLocation(fName, jsFileName,lineNumber);
			longMethodFound.add(sl);
			//System.out.println("This function is long. Starts from line " + (func.getLineno()+1) + " to line " + (func.getEndLineno()+1));
		}
		
		
		/**
		 * Detecting long parameter list
		 */
		if (numOfParam >= MAX_NUMBER_OF_PARAMETERS){
			//System.out.println("function " + func.getName() + " has " + func.getParams().size() + " parameters in line " + (func.getLineno()+1));
			SmellLocation sl = new SmellLocation(fName, jsFileName,lineNumber);
			longParameterListFound.add(sl);
		}



		System.out.println("lastFunctionDepth is :" + lastFunctionDepth);

		// keep track of nested function (scope chain)
		if (fDepth > lastFunctionDepth){
			scopeChainLength++;
			//System.out.println("scopeChainLength is :" + scopeChainLength + " at line: " + lineNumber);
			if (scopeChainLength > MAX_LENGTH_OF_SCOPE_CHAIN){
				SmellLocation sl = new SmellLocation("Long scope chain at function: " + fName, jsFileName,lineNumber);
				closureSmellLocation.add(sl);

				if (callBackFound){
					//System.out.println("Callback found in nested functions at line : " + lineNumber + " of fileName: " + fName);
					nestedCallBackFound.add(sl);
				}
				
			}
		}else
			scopeChainLength = 1;
		
		lastFunctionDepth = fDepth;
		
			
		
		if (f.getFunctionName()!=null){
			candidateObjectName = fName;
			//System.out.println("FUNCTION NAME IS: " + candidateObjectName);
		}

		/*
		   if getFunctionName==null then candidateObjectName is the name of object (filled in the last ASTNode visit) 
		   to get created by using new on the noname function
		 */
		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, fDepth, lineNumber);
		newJSObj.setJsFileName(jsFileName);
		if (!objectExists(newJSObj)){
			newJSObj.setType("FunctionCandidate"); // an object may later be instantiated form this function
			// adding parameters as properties of the object
			List<AstNode> param = f.getParams();
			for (AstNode n : param)
				newJSObj.addOwnProperty(((Name)n).getIdentifier());

			jsObjects.add(newJSObj);

			currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
			currentObjectNodeDepth = fDepth;	// setting current object node depth
			//System.out.println("Object candidate function name: " + candidateObjectName);
		}
		//System.out.println("analyseFunctionNode(): nextNameIsProperty");
		nextNameIsProperty = true;
	}	


	/**
	 * Analysing depth and determining if next name is property or object
	 */
	public void analysePropertyGetNode() {
		// nextName would be properties such as this.name = ... defined in a function 
		if (ASTNode.depth() > currentObjectNodeDepth){
			if (currentPrototype==""){	// also check if the previous identifier should actually be the object
				//System.out.println("analysePropertyGetNode(): nextNameIsProperty");
				nextNameIsProperty = true;
			}
		}
		else{ // nextName would be an object outside the function that is using a property or assigning a prototype
			nextNameIsObject = true;
			//System.out.println("analysePropertyGetNode(): nextNameIsObject");
		}
	}


	public void analyseNewExpressionNode() {
		// candidateObjectName was filled in the previous ASTNode visit
		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
		newJSObj.setJsFileName(jsFileName);
		if (!objectExists(newJSObj) && !candidateObjectName.equals("prototype")){
			jsObjects.add(newJSObj);
			currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
			currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
			//System.out.println("Object created using new from function: " + candidateObjectName);
		}else{
			//System.out.println("Already existing object: " + candidateObjectName);
		}
		//System.out.println("analyseNewExpressionNode(): nextNameIsPrototype");
		nextNameIsPrototype = true;
	}



	public void analyseFunctionCallNode() {
		
		FunctionCall fcall = (FunctionCall) ASTNode;
		//System.out.println(ASTNode.debugPrint());
		
		// check for callback
		boolean detected = false;
		for (AstNode node : fcall.getArguments())
			if (node.shortName().equals("FunctionNode")){
				//System.out.println("callback found at line : " + ( ASTNode.getLineno()+1));
				callBackFound = true;
				detected = true;
			}
		if (detected == false)
			callBackFound = false;
		
		
		/*
		   dealing with x = Object.create() and x = new Object();
		 */		currentPrototype = getPrototypeOfObjectCreateStyle(fcall.debugPrint());
		if (currentPrototype!=""){
			//System.out.println("Prototype is :" + currentPrototype);

			JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(currentIdentifier, ASTNode.depth(), ASTNode.getLineno()+1);
			newJSObj.setJsFileName(jsFileName);
			if (!objectExists(newJSObj)){		// add the new object if does not already exist
				jsObjects.add(newJSObj);
				currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
				currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
				//System.out.println("A new object is used: " + currentIdentifier);
			}

			//System.out.println("The prototype for current object: " + jsObjects.get(currentObjectIndex).getName() + " is: " + currentPrototype);
			jsObjects.get(currentObjectIndex).setPrototype(currentPrototype);

		}
		
	}


	/*
	 * Detecting long message chain
	 */
	private void checkLongMessageChain() {
		
		String ASTNodeName = ASTNode.shortName();
		
		if (ASTNodeName.equals("PropertyGet")){
			//System.out.println("consecutivePropertyGet : " + consecutivePropertyGet);
			consecutivePropertyGet++;
			lastMessageChain = consecutivePropertyGet;
			//System.out.println("lastMessageChain: " + lastMessageChain);
			
			// check if long meassage chain found
			if (consecutivePropertyGet >= MAX_LENGTH_OF_MESSAGE_CHAIN){
				SmellLocation sl = new SmellLocation("Long chain", jsFileName,(ASTNode.getLineno()+1));
				longMessageFound.add(sl);
				//System.out.println("Long message chain found!");
			}
			// if previous read was also GETPROP
			
		}else{
			consecutivePropertyGet = 0;
		}
	}


	/**
	 * Detecting objects which are created using Object.create() style and return their prototypes
	 * The astDebugFormat will be parsed to get the corresponding properties
	 * Return null if the pattern Object.create was not found
	 */
	private String getPrototypeOfObjectCreateStyle(String astDebugFormat) {
		String prototype = "";
		String tempRead = "";
		String Name = "";
		boolean foundObject = false;
		boolean foundcreate = false;
		boolean needsDot = false;	// prototype might be x.y.z as in Object.prototype
		
		int spaceCount = 0, newDepth = 0;
		for (int i=0; i<astDebugFormat.length(); i++){  //finding the next node (at any level)
			if (astDebugFormat.charAt(i) == '\t'){		// found next element at position i
				for (int j=i+1; j<astDebugFormat.length(); j++){  //finding the level
					if (astDebugFormat.charAt(j) == ' ')
						spaceCount++;
					else{
						newDepth = spaceCount/2;
						spaceCount = 0;
						while (astDebugFormat.charAt(j) != ' '){ // adding node type to the result string
							tempRead+=astDebugFormat.charAt(j);
							j++;
						}
	
						// check if first NAME is "Object" and second is "create" 
						if (tempRead.equals("NAME")){
							if (foundObject == true && foundcreate == true){  // any NAME found should be concatenated as the prototype of the object 
								if (needsDot == true)	// this is not done the first time.
									prototype += ".";	// Adds . to make prototypes such as "Object.prototype"
								prototype += getName(astDebugFormat,j);
								needsDot = true;
							}
							
							if (foundObject == false){
								Name = getName(astDebugFormat,j);
								if (Name.equals("Object"))
									foundObject = true;

							}else{	// "Object" is already found. Now search for "create"
								Name = getName(astDebugFormat,j);
								
								if (Name.equals("create"))
									foundcreate = true;
							}
						}
						tempRead="";
						break;
					}				
				}
			}
		}
		return prototype;
	}
	
	


	
	// just some parsing to get the identifier in front of the NAME
	private String getName(String astDebugFormat, int j) {
		String Name = "";
		j++; while (astDebugFormat.charAt(j) != ' ') // skipping numbers
			j++;
		j++; while (astDebugFormat.charAt(j) != ' ') // skipping numbers
			j++;
		j++;
		Name = "";
		while (astDebugFormat.charAt(j) != '\n'){ // adding node type to the result string
			Name+=astDebugFormat.charAt(j);
			j++;
		}		
		return Name;
	}


	public static void addDynamicObject(JavaScriptObjectInfo dynamicObject){

		// check if the dynamic object was already detected (by static analysis)
		for (JavaScriptObjectInfo o: jsObjects)
			if (o.getName().equals(dynamicObject.getName())){
				//System.out.println("object " + dynamicObject.getName() + " already exist!");
				// now try to add properties
				for (String own: dynamicObject.getOwnPropetries())
					o.addOwnProperty(own);
				for (String inh: dynamicObject.getInheritedPropetries())
					o.addOwnProperty(inh);
				return;
			}

		dynamicObject.setJsFileName(jsFileName);
		// add the new dynamic object to the list
		jsObjects.add(dynamicObject);
	}

	public boolean objectExists(JavaScriptObjectInfo jsObject){
		for (JavaScriptObjectInfo o: jsObjects)
			if (o.getName().equals(jsObject.getName())){
				//System.out.println("object " + jsObject.getName() + " already exist!");
				return true;
			}
		return false;
	}


	private void setCurrentObject(JavaScriptObjectInfo jsObject) {
		// find the object in the jsObjects list
		for (int i=0; i< jsObjects.size(); i++){
			if (jsObjects.get(i).getName().equals(jsObject.getName())){
				currentObjectNodeDepth = jsObjects.get(i).getASTDepth();	// current object is now at the end of jsObjects list
				currentObjectIndex = i;
				//System.out.println("Current object set to :" + jsObjects.get(i).getName() + " at depth: " + currentObjectNodeDepth);
				return;
			}
		}
	}


	private void isSwitchSmell() {

		SwitchStatement  s = (SwitchStatement)ASTNode;

		//System.out.println("switch found at line: " + (ASTNode.getLineno()+1) + " and has " + s.getCases().size() + " statements");

		if (s.getCases().size() > MAX_NUMBER_OF_SWITCHCASE){
			SmellLocation sl = new SmellLocation("switch", jsFileName,(ASTNode.getLineno()+1));
			switchFound.add(sl);
		}
	}



	
	/**
	 * Analysing JS/HTML/CSS coupling
	 * 
	 * TODO: distinguish between server-side generated codes and original inline codes
	 * 
	 */
	public static void analyseCoupling(String scopeName, String code, HashSet<String> jsInTag) {
		// counting lines of inline javascript 
		String[] lines = code.split("\r\n|\r|\n");
		//System.out.println("There are " + lines.length + " lines of JavaScript code inside your HTML");
		//System.out.println("code is: " + code);
		
		for (String jTag: jsInTag)
			jsInTagFound.add(jTag);
		
		if (!inlineJavaScriptScopeName.contains(scopeName)){
			inlineJavaScriptScopeName.add(scopeName);
			inlineJavaScriptLines += lines.length;
		}
	}
	

	
	class FunctionInfo{

		String name;
		int numberOfParameters;
		int linesOfCode;
		int lineNumber;
		
		public FunctionInfo(String name, int numberOfParameters, int linesOfCode, int lineNumber) {
			super();
			this.name = name;
			this.numberOfParameters = numberOfParameters;
			this.linesOfCode = linesOfCode;
			this.lineNumber = lineNumber;
		}
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public int getNumberOfParameters() {
			return numberOfParameters;
		}
		public void setNumberOfParameters(int numberOfParameters) {
			this.numberOfParameters = numberOfParameters;
		}
		public int getLinesOfCode() {
			return linesOfCode;
		}
		public void setLinesOfCode(int linesOfCode) {
			this.linesOfCode = linesOfCode;
		}
		public int getLineNumber() {
			return lineNumber;
		}
		public void setLineNumber(int lineNumber) {
			this.lineNumber = lineNumber;
		}
		
	}


	
	public static class SmellLocation{
		String smellyItemName;
		String jsFile;
		int lineNumber;
		
		public SmellLocation(String smellyItemName, String jsFile, int lineNumber){
			this.smellyItemName = smellyItemName;
			this.jsFile = jsFile;
			this.lineNumber = lineNumber;
		}
		
		public String getJsFile() {
			return jsFile;
		}
		public void setJsFile(String jsFile) {
			this.jsFile = jsFile;
		}
		public int getLineNumber() {
			return lineNumber;
		}
		public void setLineNumber(int lineNumber) {
			this.lineNumber = lineNumber;
		}
		public String getSmellyItemName() {
			return smellyItemName;
		}

		public void setSmellyItemName(String smellyItemName) {
			this.smellyItemName = smellyItemName;
		}	
	    
		@Override
	    public boolean equals(Object o) {
	        if (this == o) return true;
	        if (!(o instanceof SmellLocation)) return false;

	        SmellLocation sl = (SmellLocation) o;

	        if ((this.jsFile == null) ? (sl.jsFile != null) : !this.jsFile.equals(sl.jsFile)) {  
	            return false;  
	        }  
	        if (this.lineNumber != sl.lineNumber) {  
	            return false;  
	        } 
	        return true;
	    }

	    @Override
	    public int hashCode() {
	        return 1;
	    }
	    
		
	}


	// filtering large/lazy objects based on dynamically inferred object list
	public static void filterObjects(HashSet<JavaScriptObjectInfo> largeObjects,
			HashSet<JavaScriptObjectInfo> lazyObjects) {

		ArrayList<SmellLocation> itemsToRemove = new ArrayList<SmellLocation>();		
		
		for (JavaScriptObjectInfo largeObj:largeObjects)
			for (SmellLocation l:lazyObjectsLocation)
				if (l.getSmellyItemName().equals(largeObj.getName()))
					itemsToRemove.add(l);
	
		for (SmellLocation removeSmell :itemsToRemove){
			System.out.println("object " + removeSmell.getSmellyItemName() + " detected as dynamic large, removed from lazy list");
			lazyObjectsLocation.remove(removeSmell);
		}
		
		
		itemsToRemove.clear();
		for (JavaScriptObjectInfo lazyObj:lazyObjects)
			for (SmellLocation l:largeObjectsLocation)
				if (l.getSmellyItemName().equals(lazyObj.getName()))
					itemsToRemove.add(l);
	
		for (SmellLocation removeSmell :itemsToRemove){
			System.out.println("object " + removeSmell.getSmellyItemName() + " detected as dynamic lazy, removed from large list");
			largeObjectsLocation.remove(removeSmell);
		}

	}


}



