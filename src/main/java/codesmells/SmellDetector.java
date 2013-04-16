package codesmells;

import java.util.ArrayList;
import java.util.List;


import org.mozilla.javascript.ast.*;

/**
 * Main JSNose smell detection method
 * 
 * @author Aimin Milani Fard
 */
public class SmellDetector {

	// JSNose parameters for smell detection
	private static final int MAX_METHID_LENGTH = 20;
	private static final int MAX_NUMBER_OF_PARAMETERS = 4;
	private static final int MAX_LENGTH_OF_PROTOTYPE = 5;
	private static final int MAX_LENGTH_OF_MESSAGE_CHAIN = 3;
	private static final int MAX_NUMBER_OF_SWITCHCASE = 3;

	private AstNode ASTNode;

	private static ArrayList<JavaScriptObjectInfo> jsObjects = new ArrayList<JavaScriptObjectInfo>();

	private String candidateObjectName = "";		// this will be set to name of any variable and if detected as object will be added to jsObjects
	private boolean nextNameIsProperty = false;		// this is to distinguish properties of an object from other var/names
	private boolean nextNameIsPrototype = false;	// this is to distinguish prototype of an object from other var/names
	private boolean nextNameIsObject = false;		// this is to distinguish properties from an object such as for x in x.prototype = y;
	private boolean innerFunction = false;			// this is to detect if a function is inside another function
	
	private int currentObjectNodeDepth = 0;	// This is node.depth() for the current object. A property would be added if its node.depth() is higher than currentObjectNodeDepth
	private int currentObjectIndex = 0;		// This is the index of current object in jsObjects list (used to add properties/prototype)
	private String currentIdentifier = "";	// this is to keep the latest identifier as it may be detected as an object following the pattern x=Object.create
	private String currentPrototype = "";	// this is to keep the prototype x found at Object.create(x)

	private int consecutivePropertyGet = 0;	// This is to store number of consecutive getting of property used to detect long message chain 
	private int lastMessageChain = 0;		// This is to store last message chain using consecutivePropertyGet 
	private boolean ignoreDepthChange = false;		// This is also used to decide for a.b.c pattern that c is a property of b not a separate identifier 
	private static ArrayList<Integer> longMessageFound = new ArrayList<Integer>();	// keeping line number where a long message occurred
	
	private boolean LHS = false;			// This is to decide if the ASTNode is at the left hand-side of an assignment 
	private int assignmentNodeDepth = 0;	// This is to store ASTNode depth of assignment to be used for detecting LHS value 
	private boolean assignmentLHSVisited = false; 

	
	private boolean CatchClause = false;	// To detect empty Catch Clauses
	private static ArrayList<Integer> emptyCatchFound = new ArrayList<Integer>();	// keeping line number where an empty catch occurred

	private static ArrayList<Integer> longMethodFound = new ArrayList<Integer>();	// keeping line number where a long method is defined

	private static ArrayList<Integer> longParameterListFound = new ArrayList<Integer>();	// keeping line number where a long parameter list is found

	private static ArrayList<Integer> switchFound = new ArrayList<Integer>();	// keeping line number where a switch statement is found
	

//	private static ArrayList<String> globals = new ArrayList<String>();	// keeping global variables
//	private static ArrayList<String> locals = new ArrayList<String>();	// keeping local variables
//	private static ArrayList<Integer> localsLineNumber = new ArrayList<Integer>();	// keeping line number of local variables
//	private boolean nextIsLocal = false;
	
	public SmellDetector() {
		ASTNode = null;
	}

	public void SetASTNode(AstNode node) {
		ASTNode = node;
	}

	/**
	 * Showing list of smells when all AST nodes were visited. The method is static to be called in JSModifyProxyPlugin.modifyJS()
	 */
	public static void showResults(){
		// TODO: write in text file
		System.out.println("********** CODE SMELL REPORT **********");
		
		analyseObjecsList();
		
		System.out.println("********** EMPTY CATCH **********");
		System.out.println("Total number of empty catch clause: " + emptyCatchFound.size());
		for (int i=0;i<emptyCatchFound.size();i++)
			System.out.println("Empty catch clause found at line: " + emptyCatchFound.get(i));

		// Globals are done in Crawler.java
		
		/*
		System.out.println("********** LARGE OBJECT **********");
		System.out.println("Total number of long messages: " + longMessageFound.size());
		for (int i=0;i<longMessageFound.size();i++)
			System.out.println("Long message chain found at line: " + longMessageFound.get(i));	
		
		
		System.out.println("********** LAZY OBJECT **********");
		System.out.println("Total number of long messages: " + longMessageFound.size());
		for (int i=0;i<longMessageFound.size();i++)
			System.out.println("Long message chain found at line: " + longMessageFound.get(i));
		
		*/
		
		
		System.out.println("********** LONG MESSAGE **********");
		System.out.println("Total number of long messages: " + longMessageFound.size());
		for (int i=0;i<longMessageFound.size();i++)
			System.out.println("Long message chain found at line: " + longMessageFound.get(i));


		System.out.println("********** LONG METHOD/FUNCTION **********");
		System.out.println("Total number of long methods: " + longMethodFound.size());
		for (int i=0;i<longMethodFound.size();i++)
			System.out.println("Long method found at line: " + longMethodFound.get(i));

		
		System.out.println("********** LONG PARAMETER LIST **********");
		System.out.println("Number of functions with long parameter list: " + longParameterListFound.size());
		for (int i=0;i<longParameterListFound.size();i++)
			System.out.println("Long parameter list found at line: " + longParameterListFound.get(i));

		// Refused bequest
		
		System.out.println("********** SWITCH STATEMENT **********");
		System.out.println("Number of smelly switch statements: " + switchFound.size());
		for (int i=0;i<switchFound.size();i++)
			System.out.println("Smelly switch statement found at line: " + switchFound.get(i));
		
		
		
		
		System.out.println("********** OBJECT LIST **********");
		for (JavaScriptObjectInfo o: jsObjects)
			System.out.println(o);
	}
	
	/**
	 * Analysing jsObjects list to calculate used/unused inherited properties 
	 * The method is static to be used by printObject()
	 */
	public static void analyseObjecsList() {
		
		String protptype = "";
		
		//	usedInheritedPropetries = intersection of ownPropetries and inheritedPropetries 
		//	usedInheritedPropetries = inheritedPropetries - ownPropetries
		ArrayList<String> ownPropetries = new ArrayList<String>();	// only own
		ArrayList<String> usedPropetries = new ArrayList<String>();	// both own and inherited
		ArrayList<String> inheritedPropetries = new ArrayList<String>();		// ownPropetries of the prototype (if has one)
		ArrayList<String> usedInheritedPropetries = new ArrayList<String>();	// Inherited properties used or overwritten
		ArrayList<String> notUsedInheritedPropetries = new ArrayList<String>();	// Inherited properties not used or overwritten

		ArrayList<String> delegatedPropetries = new ArrayList<String>();		// Delegated properties which are defined some where in the prototype chain 
		ArrayList<JavaScriptObjectInfo> prototypeChain = new ArrayList<JavaScriptObjectInfo>();				// Storing the prototype chain of an object 

		
		for (JavaScriptObjectInfo jso : jsObjects){
			usedInheritedPropetries.clear();
			notUsedInheritedPropetries.clear();
			delegatedPropetries.clear();
			prototypeChain.clear();
			
			ownPropetries = jso.getOwnPropetries();
			System.out.println("ownPropetries of :" + jso.getName() + " is: " + ownPropetries);
			
			usedPropetries = jso.getUsedPropetries();
			System.out.println("usedPropetries of :" + jso.getName() + " is: " + usedPropetries);
			
			//detecting not-own but used properties
			for (String used: usedPropetries)
				if (!ownPropetries.contains(used)){
					System.out.println("property: " + used + " was delegated to prototype chain");
					delegatedPropetries.add(used);
				}

			protptype = jso.getPrototype();
			if (protptype!=""){

				System.out.println("protptype of :" + jso.getName() + " is: " + protptype);

				for (JavaScriptObjectInfo proto : jsObjects)
					if (proto.getName().equals(protptype)){
						
						prototypeChain.add(proto);
						
						inheritedPropetries = proto.getOwnPropetries();

						jso.setInheritedPropetries(inheritedPropetries);
						System.out.println("inheritedPropetries of :" + jso.getName() + " is: " + jso.getInheritedPropetries());


						for (String prop : inheritedPropetries){
							if (ownPropetries.contains(prop))	// finding used/overrode inherited properties
								usedInheritedPropetries.add(prop);
							else
								notUsedInheritedPropetries.add(prop);
						}

						jso.setUsedInheritedPropetries(usedInheritedPropetries);
						jso.setNotUsedInheritedPropetries(notUsedInheritedPropetries);

						System.out.println("usedInheritedPropetries of :" + jso.getName() + " is: " + jso.getUsedInheritedPropetries());

						System.out.println("notUsedInheritedPropetries of :" + jso.getName() + " is: " + jso.getNotUsedInheritedPropetries());



						// detecting refused bequest
						if ( (double)usedInheritedPropetries.size() / (double)inheritedPropetries.size() < 0.33)
							System.out.println("Detected refused bequest for object: " + jso.getName());



						// detecting prototype-chain
						protptype = proto.getPrototype();
						while(protptype != ""){ 
							for (JavaScriptObjectInfo o : jsObjects)
								if (o.getName().equals(protptype)){
									protptype = o.getPrototype();
									prototypeChain.add(o);
									break;
								}
						}

						//System.out.println("prototypeChain is: " + prototypeChain);
						if (prototypeChain.size() >= MAX_LENGTH_OF_PROTOTYPE)
							System.out.println("Long prototype chain found for object: " + jso.getName() + " defined at line: " + jso.getLineNumber());

						// detecting delegation in prototype-chain
						for (String delProp : delegatedPropetries){
							boolean found = false;
							for (JavaScriptObjectInfo o : prototypeChain){
								inheritedPropetries = o.getOwnPropetries();
								if (inheritedPropetries.contains(delProp)){	// finding used/overrode inherited properties
									System.out.println("delegated property: " + delProp + " was found in object: " + o.getName());
									found = true;
									break;
								}
							}
							if (found == false)
								System.out.println("could not found delegated property: " + delProp + " in prototypeChain");
						}

						break;

					}

			}

			System.out.println();
			
		}
	}

	/**
	 * Analysing ASTNode for code smells.
	 * This is to the following smells:
	 * 1. Long list of parameters
	 * 2. Long methods
	 * 3. Long message chain
	 * 4. Lazy object
	 * 5. Long prototype chain
	 * 
	 * @param node
	 *            The AST node that is currently visited.
	 */
	public void analyseAstNode() {

	
		String ASTNodeName = ASTNode.shortName();
		
		System.out.println("node.shortName() : " + ASTNode.shortName());
		System.out.println("node.depth() : " + ASTNode.depth());
		System.out.println("node.getLineno() : " + (ASTNode.getLineno()+1));
		
		checkLongMessageChain();   // also used to detect message chain used in object recognition

		// check if we are in the up the currentObjectNodeDepth
		if (ASTNode.depth() < currentObjectNodeDepth && lastMessageChain==1 && ignoreDepthChange==false){  // dealing with a.b.c = ... patterns  
			nextNameIsProperty = false;
			//nextNameIsObject = true;
			//System.out.println("analyseAstNode(): Level changed! nextNameIsObject");
			System.out.println("analyseAstNode(): Level changed! nextName is not property anymore");
		}
		
		// check if we are in LHS of the current assignment
		if (ASTNode.depth()==assignmentNodeDepth+1){
			if (assignmentLHSVisited == false){
				assignmentLHSVisited = true;
			}else
				LHS = false;
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
		else if (ASTNodeName.equals("Block"))
			analyseBlock();		
		else if (ASTNodeName.equals("SwitchCase"))
			isSwitchSmell();
		
		
		



		
		//System.out.println("node.toSource() : " + node.toSource());
		
		System.out.println();
		
		//System.out.println("node.getType() : " + node.getType());
		//System.out.println("node.getAstRoot() : " + node.getAstRoot());
		//System.out.println("node.debugPrint() : " + node.debugPrint());

		/**** TODO
		
		//	System.out.println("printing ASTNode " + ASTNode.toSource());

		
		if (!((ASTNode instanceof FunctionNode || ASTNode instanceof ReturnStatement || ASTNode instanceof SwitchCase || 
				ASTNode instanceof AstRoot || ASTNode instanceof ExpressionStatement || ASTNode instanceof BreakStatement || 
				ASTNode instanceof ContinueStatement || ASTNode instanceof ThrowStatement || ASTNode instanceof VariableDeclaration))) {// || node instanceof ExpressionStatement || node instanceof BreakStatement || node instanceof ContinueStatement || node instanceof ThrowStatement || node instanceof VariableDeclaration || node instanceof ReturnStatement || node instanceof SwitchCase)) {
			return;
		}
		****/
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
				emptyCatchFound.add((ASTNode.getLineno()+1));
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
		System.out.println("idenfifier: " + candidateObjectName );
		currentIdentifier = candidateObjectName;	// this is to be used in method analyseFunctionCallNode() if a pattern of Object.create(x) was detected where x is the currentPrototype
		

		/*
		  Adding a property of the object or if it is the keyword "prototype" or "__proto__" setting the prototype to Object.prototype
		 */
		if (nextNameIsProperty == true){		// check if next name is a property name, default is false

			System.out.println("NameIsProperty=true for the name: " + ((Name)ASTNode).getIdentifier());

			if (((Name)ASTNode).getIdentifier().equals("prototype") || ((Name)ASTNode).getIdentifier().equals("__proto__")){	// check if prototype object is declared (if a pattern of x.prototype found)
				/**
				 * check if it is "Object.prototype" and not "x.prototype" = y
				 */
				System.out.println("should find a prototype for current object: " + jsObjects.get(currentObjectIndex).getName());
				
				nextNameIsProperty = false;

			}else{	
				/*
    			  Adding a property to the current object
				 */
				if (!((Name)ASTNode).getIdentifier().equals(jsObjects.get(currentObjectIndex).getName())){ // ignoring to add the function name as a property of the object
					System.out.println("property found: " + ((Name)ASTNode).getIdentifier() + " for object: " + jsObjects.get(currentObjectIndex).getName());
					
					if (LHS==true){
						System.out.println("THIS IS AN OWN PROPERTY!");
						jsObjects.get(currentObjectIndex).addOwnProperty(((Name)ASTNode).getIdentifier());
						jsObjects.get(currentObjectIndex).addUsedProperty(((Name)ASTNode).getIdentifier());
					}else{
						System.out.println("THIS IS A USED PROPERTY!");
						jsObjects.get(currentObjectIndex).addUsedProperty(((Name)ASTNode).getIdentifier());
					}
					
					
					System.out.println("lastMessageChain is :" + lastMessageChain);
					if (lastMessageChain==1){ // normal case (no nested object.object.object...)
						nextNameIsProperty = true;
						ignoreDepthChange = false;
						System.out.println("ignoreDepthChange = false");
					}
					else{ // dealing with a.b.c = ... pattern

						System.out.println("lastMessageChain is : " + lastMessageChain + " so " + ((Name)ASTNode).getIdentifier() + " should be a new object");

						candidateObjectName = ((Name)ASTNode).getIdentifier();
						JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
						if (!objectExists(newJSObj)){		// add the new object if does not already exist
							jsObjects.add(newJSObj);
							currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
							currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
							System.out.println("A new object is used: " + candidateObjectName);
						}
						System.out.println("object in use: " + candidateObjectName);
						nextNameIsObject = false;
						System.out.println("analyseNameNode(): nextNameIsProperty");
						nextNameIsProperty = true;  // to read next name as the property of this object such as x.propX. propX may also be prototype and should be avoided to add as property

						ignoreDepthChange = true;
						System.out.println("ignoreDepthChange = true");

						lastMessageChain--;
						
						System.out.println("lastMessageChain is :" + lastMessageChain);
						
					}
				}
			}
		}


		/*
		  check if next name is a prototype object name, default is false
		 */
		if (nextNameIsPrototype == true){
			System.out.println("prototype found: " + ((Name)ASTNode).getIdentifier() + " for object: " + jsObjects.get(currentObjectIndex).getName());
			jsObjects.get(currentObjectIndex).setPrototype(((Name)ASTNode).getIdentifier());
			nextNameIsPrototype = false;
		}
		
		
		if (nextNameIsObject == true){	// check if next name is an object name, default is false
			// either an already found object or a new object. Example is oldObj.newProperty or newObj.newProperty
			candidateObjectName = ((Name)ASTNode).getIdentifier();
			JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
			if (!objectExists(newJSObj)){		// add the new object if does not already exist
				jsObjects.add(newJSObj);
				currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
				currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
				System.out.println("A new object is used: " + candidateObjectName);
			}else{
				
				//JavaScriptObjectInfo existingObject = getJSObject(jsObjects,newJSObj);
				
				setCurrentObject(newJSObj);
				
			}
			System.out.println("object in use: " + candidateObjectName);
			nextNameIsObject = false;
			System.out.println("analyseNameNode(): nextNameIsProperty");
			nextNameIsProperty = true;  // to read next name as the property of this object such as x.propX. propX may also be prototype and should be avoided to add as property
		}

	}





	/**
	 * Extracting object literals in javaScript
	 */
	public void analyseObjectLiteralNode() {
		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
		newJSObj.setType("ObjectLiteral");
		jsObjects.add(newJSObj);
		currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
		currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
		System.out.println("Object literal: " + candidateObjectName);
	}

	public void analyseObjectPropertyNode() {
		if (ASTNode.depth() > currentObjectNodeDepth){ // check depth of the prop with current object's depth
				System.out.println("analyseObjectPropertyNode(): nextNameIsProperty");
				nextNameIsProperty = true;
		}
		//else
		//	nextNameIsProperty = false;
	}


	/**
	 * Extracting objects created by new keyword in javaScript
	 */
	public void analyseFunctionNode() {

		// detecting long methods
		isLongMethod();
		
		// detecting long parameter list
		hasManyParameters();
		
		FunctionNode f = (FunctionNode) ASTNode;
		//System.out.println(f.debugPrint());
		
		if (f.getFunctionName()!=null){
			candidateObjectName = f.getFunctionName().getIdentifier();
			System.out.println("FUNCTION NAME IS: " + candidateObjectName);
		}

		/*
		   if getFunctionName==null then candidateObjectName is the name of object (filled in the last ASTNode visit) 
		   to get created by using new on the noname function
		 */
		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
		if (!objectExists(newJSObj)){
			newJSObj.setType("FunctionCandidate"); // an object may later be instantiated form this function
			// adding parameters as properties of the object
			List<AstNode> param = f.getParams();
			for (AstNode n : param)
				newJSObj.addOwnProperty(((Name)n).getIdentifier());

			jsObjects.add(newJSObj);

			currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
			currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
			System.out.println("Object candidate function name: " + candidateObjectName);
		}
		System.out.println("analyseFunctionNode(): nextNameIsProperty");
		nextNameIsProperty = true;
	}	



	public void analysePropertyGetNode() {
		// nextName would be properties such as this.name = ... defined in a function 
		if (ASTNode.depth() > currentObjectNodeDepth){
			if (currentPrototype==""){	// also check if the previous identifier should actually be the object
				System.out.println("analysePropertyGetNode(): nextNameIsProperty");
				nextNameIsProperty = true;
			}
		}
		else{ // nextName would be an object outside the function that is using a property or assigning a prototype
			nextNameIsObject = true;
			System.out.println("analysePropertyGetNode(): nextNameIsObject");
		}
	}


	public void analyseNewExpressionNode() {
		// candidateObjectName was filled in the previous ASTNode visit
		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth(), ASTNode.getLineno()+1);
		if (!objectExists(newJSObj) && !candidateObjectName.equals("prototype")){
			jsObjects.add(newJSObj);
			currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
			currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
			System.out.println("Object created using new from function: " + candidateObjectName);
		}else{
			System.out.println("Already existing object: " + candidateObjectName);
		}
		System.out.println("analyseNewExpressionNode(): nextNameIsPrototype");
		nextNameIsPrototype = true;
	}



	public void analyseFunctionCallNode() {
		/*
		   dealing with x = Object.create() and x = new Object();
		 */
		FunctionCall fcall = (FunctionCall) ASTNode;
		currentPrototype = getPrototypeOfObjectCreateStyle(fcall.debugPrint());
		if (currentPrototype!=""){
			System.out.println("Prototype is :" + currentPrototype);

			JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(currentIdentifier, ASTNode.depth(), ASTNode.getLineno()+1);
			if (!objectExists(newJSObj)){		// add the new object if does not already exist
				jsObjects.add(newJSObj);
				currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
				currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
				System.out.println("A new object is used: " + currentIdentifier);
			}

			System.out.println("The prototype for current object: " + jsObjects.get(currentObjectIndex).getName() + " is: " + currentPrototype);
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
			System.out.println("lastMessageChain: " + lastMessageChain);
			// check if long meassage chain found
			if (consecutivePropertyGet >= MAX_LENGTH_OF_MESSAGE_CHAIN){
				longMessageFound.add((ASTNode.getLineno()+1));
				//System.out.println("Long message chain found!");
			}
			// if previous read was also GETPROP
			
		}else{
			consecutivePropertyGet = 0;
		}
	}


	
	/*
	 * Detecting long parameter list
	 */
	private boolean hasManyParameters(){
		FunctionNode func = (FunctionNode) ASTNode;
		if (func.getParams().size() >= MAX_NUMBER_OF_PARAMETERS){
			//System.out.println("function " + func.getName() + " has " + 
			//		func.getParams().size() + " parameters in line " + (func.getLineno()+1));
			longParameterListFound.add((ASTNode.getLineno()+1));
			return true;
		}
		return false;
	}

	/*
	 * Detecting long method/function
	 */
	private boolean isLongMethod(){
		FunctionNode func = (FunctionNode) ASTNode;
		if (func.getEndLineno() - func.getLineno() > MAX_METHID_LENGTH){
			longMethodFound.add((ASTNode.getLineno()+1));
			//System.out.println("This function is long. Starts from line " + (func.getLineno()+1) + " to line " + (func.getEndLineno()+1));
			return true;
		}
		return false;
	}
		




	// Amin: making a string representation for ast
	/*
87	      CALL 4 31
87	        GETPROP 0 13
87	          NAME 0 6 Object
94	          NAME 7 6 create
101	        GETPROP 14 16
101	          NAME 0 6 Object
108	          NAME 7 9 prototype

134	        CALL 7 21
134	          GETPROP 0 13
134	            NAME 0 6 Object
141	            NAME 7 6 create
148	          NAME 14 6 person
	 */
	
	/**
	 * Detecting object created by using Object.create() style and return its prototype
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



	public boolean objectExists(JavaScriptObjectInfo jsObject){
		for (JavaScriptObjectInfo o: jsObjects)
			if (o.getName().equals(jsObject.getName())){
				System.out.println("object " + jsObject.getName() + " already exist!");
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
				System.out.println("Current object set to :" + jsObjects.get(i).getName() + " at depth: " + currentObjectNodeDepth);
				return;
			}
		}
	}


	/**
	 * TODO
	 */
	private void isSwitchSmell() {

		//System.out.println("switch found at line: " + (ASTNode.getLineno()+1));
		
		SwitchCase sc = (SwitchCase)ASTNode;
		if (sc.getStatements().size() > MAX_NUMBER_OF_SWITCHCASE)
			switchFound.add((ASTNode.getLineno()+1));
	}



	
	/**
	 * Analysing JS/HTML/CSS coupling
	 * 
	 * TODO: distinguish between server-side generated codes and original inline codes
	 * 
	 */
	public static void analyseCoupling(String code) {
		// counting lines of inline javascript 
		String[] lines = code.split("\r\n|\r|\n");
		System.out.println("There are " + lines.length + " lines of JavaScript code inside your HTML");
		System.out.println("code is: " + code);
	}
	
	
	
}



