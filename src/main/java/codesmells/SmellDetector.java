package codesmells;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.*;

/**
 * Main JSNose smell detection method
 * 
 * @author Aimin Milani Fard
 */
public class SmellDetector {

	// JSNose parameters for smell detection
	private static final int MAX_METHID_LENGTH = 20;
	private static final int MAX_NUMBERS_OF_PARAMETERS = 4;
	private static final int MAX_LENGTH_OF_PROTOTYPE = 5;
	private static final int MAX_LENGTH_OF_MESSAGE_CHAIN = 3;

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

	private int consecutivePropertyGet = 0;	// This is to store number of consecutive getting of property used to detect long mesasage chain 
	
	public SmellDetector() {
		ASTNode = null;
	}

	public void SetASTNode(AstNode node) {
		ASTNode = node;
	}


	
	/**
	 * Analysing abstract syntax tree for code smells.
	 * This is to the following smells:
	 * 1. Long list of parameters
	 * 2. Long methods
	 * 3. Long message chain
	 * 4. Middle man
	 * 5. Long prototype chain
	 * 
	 * @param node
	 *            The AST node that is currently visited.
	 */
	public void analyseAstNode() {

		//int nt = ASTNode.getType();
		//String name = Token.typeToName(nt);
		String ASTNodeName = ASTNode.shortName();
		
		//System.out.println("name :" + name);
		System.out.println("node.shortName() : " + ASTNode.shortName());
		System.out.println("node.depth() : " + ASTNode.depth());
		System.out.println("node.getLineno() : " + (ASTNode.getLineno()+1));
		
		/*
		checkLongMessageChain(ASTNodeName);

		if (ASTNode instanceof FunctionNode) {
			isLongMethod();
			hasManyParameters();
		}
		*/
		
		
		if (ASTNodeName.equals("Name"))
			analyseNameNode();
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


		if (ASTNode instanceof SwitchCase) {
			isSwitchSmell();
		}
		****/
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
		  Adding a property of the object or if it is the keyword "prototype" setting the prototype to Object.prototype
		 */
		if (nextNameIsProperty == true){		// check if next name is a property name, default is false

			System.out.println("nextNameIsProperty, now checking the name: " + ((Name)ASTNode).getIdentifier());

			if (((Name)ASTNode).getIdentifier().equals("prototype")){	// check if prototype object is declared (if a pattern of x.prototype found)
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
					jsObjects.get(currentObjectIndex).addOwnProperty(((Name)ASTNode).getIdentifier());
					nextNameIsProperty = false;
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
		}else{			
			if (nextNameIsObject == true){	// check if next name is an object name, default is false
				// either an already found object or a new object. Example is oldObj.newProperty or newObj.newProperty
				candidateObjectName = ((Name)ASTNode).getIdentifier();
				JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth());
				if (!objectExists(jsObjects,newJSObj)){		// add the new object if does not already exist
					jsObjects.add(newJSObj);
					currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
					currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
					System.out.println("A new object is used: " + candidateObjectName);
				}else{
					
					//JavaScriptObjectInfo existingObject = getJSObject(jsObjects,newJSObj);
					//currentObjectNodeDepth = existingObject.getASTDepth();	// current object is now at the end of jsObjects list
				}
				System.out.println("object in use: " + candidateObjectName);
				nextNameIsObject = false;
				System.out.println("analyseNameNode(): nextNameIsProperty");
				nextNameIsProperty = true;  // to read next name as the property of this object such as x.propX. propX may also be prototype and should be avoided to add as property
			}
		}

	}




	/**
	 * Extracting object literals in javaScript
	 */
	public void analyseObjectLiteralNode() {
		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth());
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

		FunctionNode f = (FunctionNode) ASTNode;
		System.out.println(f.debugPrint());
		
		if (((FunctionNode)ASTNode).getFunctionName()!=null){
			candidateObjectName = ((FunctionNode)ASTNode).getFunctionName().getIdentifier();
			System.out.println("FUNCTION NAME IS: " + candidateObjectName);
		}

		/*
		   if getFunctionName==null then candidateObjectName is the name of object (filled in the last ASTNode visit) 
		   to get created by using new on the noname function
		 */
		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth());
		if (!objectExists(jsObjects,newJSObj)){
			newJSObj.setType("FunctionCandidate"); // an object may later be instantiated form this function
			// adding parameters as properties of the object
			FunctionNode func = (FunctionNode) ASTNode;
			List<AstNode> param = func.getParams();
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
		JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth());
		if (!objectExists(jsObjects,newJSObj)){
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

			JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(currentIdentifier, ASTNode.depth());
			if (!objectExists(jsObjects,newJSObj)){		// add the new object if does not already exist
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
	private void checkLongMessageChain(String ASTNodeName) {
		if (ASTNodeName.equals("PropertyGet")){
			//System.out.println("consecutivePropertyGet : " + consecutivePropertyGet);
			consecutivePropertyGet++;
			// check if long meassage chain found
			if (consecutivePropertyGet >= MAX_LENGTH_OF_MESSAGE_CHAIN)
				System.out.println("Long message chain found!");
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
		if (func.getParams().size() >= MAX_NUMBERS_OF_PARAMETERS){
			System.out.println("function " + func.getName() + " has " + 
					func.getParams().size() + " parameters in line " + (func.getLineno()+1));

			//System.out.println("also it has " + func.getParamCount());
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
			System.out.println("This functtion is large. Starts from line " + (func.getLineno()+1) + " to line " + (func.getEndLineno()+1));
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


	
	public static void printObject(){
		for (JavaScriptObjectInfo o: jsObjects)
			System.out.println(o);
	}
	
	public boolean objectExists(ArrayList<JavaScriptObjectInfo> jsObjectList, JavaScriptObjectInfo jsObject){
		for (JavaScriptObjectInfo o: jsObjectList)
			if (o.getName().equals(jsObject.getName())){
				System.out.println("object " + jsObject.getName() + " already exist!");
				return true;
			}
		return false;
	}


	public JavaScriptObjectInfo getJSObject(ArrayList<JavaScriptObjectInfo> jsObjectList, JavaScriptObjectInfo jsObject){
		for (JavaScriptObjectInfo o: jsObjectList)
			if (o.getName().equals(jsObject.getName())){
				System.out.println("object " + jsObject.getName() + " already exist!");
				return o;
			}
		return null;
	}


	/**
	 * TODO
	 */
	private void isSwitchSmell() {
		//Add block around all statements in the switch case
		SwitchCase sc = (SwitchCase)ASTNode;
		List<AstNode> statements = sc.getStatements();
		List<AstNode> blockStatement = new ArrayList<AstNode>();
		Block b = new Block();

		if (statements != null) {
			Iterator<AstNode> it = statements.iterator();
			while (it.hasNext()) {
				AstNode stmnt = it.next();
				b.addChild(stmnt);
			}

			blockStatement.add(b);
			sc.setStatements(blockStatement);
		}
	}


}



