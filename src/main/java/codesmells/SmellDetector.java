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

	private AstNode ASTNode;

	private static ArrayList<JavaScriptObjectInfo> jsObjects = new ArrayList<JavaScriptObjectInfo>();

	public static void printObject(){
		for (JavaScriptObjectInfo o: jsObjects)
			System.out.println(o);
	}



	private String candidateObjectName = "";		// this will be set to name of any variable and if detected as object will be added to jsObjects
	private boolean nextNameIsProperty = false;		// this is to distinguish properties of an object from other var/names
	private boolean nextNameIsPrototype = false;	// this is to distinguish prototype of an object from other var/names
	private boolean nextNameIsObject = false;		// this is to distinguish properties from an object such as for x in x.prototype = y;
	private boolean foundObjectKeywork = false;		// this is to find Object.creat/Object.prototype

	private int currentObjectNodeDepth = 0;	// This is node.depth() for the current object. A property would be added if its node.depth() is higher than currentObjectNodeDepth
	private int currentObjectIndex = 0;		// This is the index of current object in jsObjects list (used to add properties/prototype)
	private String currentIdentifier = "";	// this is to keep the latest identifier as it may be detected as an object following the pattern x=Object.create


	public SmellDetector() {
		ASTNode = null;
	}

	public void SetASTNode(AstNode node) {
		ASTNode = node;
	}

	public boolean objectExists(ArrayList<JavaScriptObjectInfo> jsObjectList, JavaScriptObjectInfo jsObject){
		for (JavaScriptObjectInfo o: jsObjectList)
			if (o.getName().equals(jsObject.getName())){
				System.out.println("object " + jsObject.getName() + " already exist!");
				return true;
			}
		return false;
	}



	/**
	 * checking if name is the name of an object, function, property, etc.
	 */
	public void analyseNameNode() {		

		/*
		   dealing with x = Object.create() and x = new Object();
		 */
		if ((((Name)ASTNode).getIdentifier().equals("Object"))){

			// for the case of x = new Object();
			if (nextNameIsPrototype==true){  // check if it is this pattern: x = new Object();
				System.out.println("prototype of object: " + jsObjects.get(currentObjectIndex).getName() + " is Object");
				jsObjects.get(currentObjectIndex).setPrototype("Object");
				nextNameIsPrototype = false;
			}else{
				// add the latest detected identifier as an object and add it to the list
				// next time it will automatically add the prototype to this new object
				JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(currentIdentifier, ASTNode.depth());
				if (!objectExists(jsObjects,newJSObj)){			// add the new object if does not already exist
					jsObjects.add(newJSObj);
					currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
					currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
					System.out.println("A new object is used: " + currentIdentifier);
				}
			}
			System.out.println("Object keyword found!");
			foundObjectKeywork = true;
			return;
		}
		
		if ((((Name)ASTNode).getIdentifier().equals("create"))){
			if (foundObjectKeywork == true){
				System.out.println("create found!");
				System.out.println("analyseNameNode(): nextNameIsPrototype");
				nextNameIsPrototype = true;
				foundObjectKeywork = false;
			}else{
				System.out.println("ERRRROE!");
			}
			return;
		}


		// setting candidateObjectName and currentIdentifier
		candidateObjectName = ((Name)ASTNode).getIdentifier();
		System.out.println("idenfifier: " + candidateObjectName );
		currentIdentifier = candidateObjectName;


		/*
		  Adding a property of the object or if it is the keyword "prototype" setting the prototype to Object.prototype
		 */
		if (nextNameIsProperty == true){		// check if next name is a property name, default is false

			System.out.println("nextNameIsProperty, now checking the name: " + ((Name)ASTNode).getIdentifier());

			if (((Name)ASTNode).getIdentifier().equals("prototype")){	// check if prototype object is declared (if a pattern of x.prototype found)
				/**
				 * check if it is "Object.prototype" and not "x.prototype" = y
				 */
				if (foundObjectKeywork == true){ 
					System.out.println("Object.prototype is the prototype for current object: " + jsObjects.get(currentObjectIndex).getName());
					jsObjects.get(currentObjectIndex).setPrototype("Object.prototype");
					foundObjectKeywork = false;
				}else{	
					System.out.println("should find a prototype for current object: " + jsObjects.get(currentObjectIndex).getName());
				}

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
			
			return;
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
				}
				System.out.println("object in use: " + candidateObjectName);
				nextNameIsObject = false;
				System.out.println("analyseNameNode(): nextNameIsProperty");
				nextNameIsProperty = true;  // to read next name as the property of this object such as x.propX. propX may also be prototype and should be avoided to add as property
			}
		}












//		if (nextNameIsProperty == false){		// check if next name is a property name, default is false
//			if (nextNameIsPrototype == false){	// check if next name is a prototype object name, default is false
//				if (nextNameIsObject == false){	// check if next name is an object name, default is false
//					candidateObjectName = ((Name)ASTNode).getIdentifier();
//					System.out.println("idenfifier: " + candidateObjectName );
//					currentIdentifier = candidateObjectName;
//				}else{	
//					// either an already found object or a new object. Example is oldObj.newProperty or newObj.newProperty
//					candidateObjectName = ((Name)ASTNode).getIdentifier();
//					JavaScriptObjectInfo newJSObj = new JavaScriptObjectInfo(candidateObjectName, ASTNode.depth());
//					if (!objectExists(jsObjects,newJSObj)){		// add the new object if does not already exist
//						jsObjects.add(newJSObj);
//						currentObjectIndex = jsObjects.size()-1;	// current object is now at the end of jsObjects list
//						currentObjectNodeDepth = ASTNode.depth();	// setting current object node depth
//						System.out.println("A new object is used: " + candidateObjectName);
//					}
//					System.out.println("object in use: " + candidateObjectName);
//					nextNameIsObject = false;
//					nextNameIsProperty = true;  // to read next name as the property of this object such as x.propX. propX may also be prototype and should be avoided to add as property
//				}
//			}else{
//				System.out.println("prototype found: " + ((Name)ASTNode).getIdentifier() + " for object: " + jsObjects.get(currentObjectIndex).getName());
//				jsObjects.get(currentObjectIndex).setPrototype(((Name)ASTNode).getIdentifier());
//				nextNameIsPrototype = false;
//			}
//		}else{
//
//			System.out.println(((Name)ASTNode).getIdentifier());
//
//			if (!((Name)ASTNode).getIdentifier().equals("prototype")){	// check if prototype object is declared
//				if (!((Name)ASTNode).getIdentifier().equals(jsObjects.get(currentObjectIndex).getName())){ // ignoring to add the function name as a property of the object
//					System.out.println("property found: " + ((Name)ASTNode).getIdentifier() + " for object: " + jsObjects.get(currentObjectIndex).getName());
//					jsObjects.get(currentObjectIndex).addOwnProperty(((Name)ASTNode).getIdentifier());
//					nextNameIsProperty = false;
//				}
//			}else{
//				if (foundObjectKeywork == true){  // check if it is "Object.prototype" not "x.prototype" = y
//					System.out.println("Object.prototype is the prototype for current object: " + jsObjects.get(currentObjectIndex).getName());
//					jsObjects.get(currentObjectIndex).setPrototype("Object.prototype");
//					foundObjectKeywork = false;
//					nextNameIsProperty = false;
//				}else{	
//					System.out.println("should find a prototype for current object: " + jsObjects.get(currentObjectIndex).getName());
//					nextNameIsProperty = false;
//				}
//			}
//		}
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
		
		// should nextname be property????????
	}

	public void analyseObjectPropertyNode() {
		System.out.println("analyseObjectPropertyNode(): nextNameIsProperty");
		nextNameIsProperty = true;
	}


	/**
	 * Extracting objects created by new keyword in javaScript
	 */
	public void analyseFunctionNode() {

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
			System.out.println("analysePropertyGetNode(): nextNameIsProperty");
			nextNameIsProperty = true;
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





	/**
	 * Analysing abstract syntax tree for code smells.
	 * This is to the following smells:
	 * 1. long list of parameters
	 * 2. long methods
	 *
	 * 
	 * @param node
	 *            The AST node that is currently visited.
	 */
	public void analyseAstNode() {

		int nt = ASTNode.getType();
		String name = Token.typeToName(nt);
		
		System.out.println("name :" + name);
		System.out.println("node.shortName() : " + ASTNode.shortName());
		
		if (ASTNode.shortName().equals("Name"))
			analyseNameNode();
		else if (ASTNode.shortName().equals("ObjectLiteral"))
			analyseObjectLiteralNode();
		else if (ASTNode.shortName().equals("ObjectProperty"))
			analyseObjectPropertyNode();
		else if (ASTNode.shortName().equals("FunctionNode"))
			analyseFunctionNode();
		else if (ASTNode.shortName().equals("PropertyGet"))  // this is for inner function defined properties such as this.name = ...
			analysePropertyGetNode();
		else if (ASTNode.shortName().equals("NewExpression"))
			analyseNewExpressionNode();
		else if (ASTNode.shortName().equals("FunctionCall")){

			FunctionCall fcall = (FunctionCall) ASTNode;
			System.out.println(fcall.debugPrint());
			List<AstNode> args = fcall.getArguments();
			for (AstNode n : args)
				//newJSObj.addOwnProperty(((Name)n).getIdentifier());
				System.out.println(n.debugPrint());

		}

	
/*
		if (ASTNode.shortName().equals(("GETPROP")){
			consecutiveGETPROP++;
			// check if long meassage chain found
			if (consecutiveGETPROP > 2)
				System.out.println("Long message chain found!");
			// if previous read was also GETPROP
			
		}else{
			consecutiveGETPROP = 0;
		}
	*/	
		
		
		System.out.println("node.depth() : " + ASTNode.depth());
		System.out.println("node.getLineno() : " + (ASTNode.getLineno()+1));

		//System.out.println("node.toSource() : " + node.toSource());
		
		System.out.println();
		
		//System.out.println("node.getType() : " + node.getType());
		//System.out.println("node.getAstRoot() : " + node.getAstRoot());
		//System.out.println("node.debugPrint() : " + node.debugPrint());

		//node.getAbsolutePosition()
		//node.getPosition()
		//node.getLength()

		if (1==1)
			return;
		
		
		System.out.println("printing ASTNode " + ASTNode.toSource());

		
		if (!((ASTNode instanceof FunctionNode || ASTNode instanceof ReturnStatement || ASTNode instanceof SwitchCase || 
				ASTNode instanceof AstRoot || ASTNode instanceof ExpressionStatement || ASTNode instanceof BreakStatement || 
				ASTNode instanceof ContinueStatement || ASTNode instanceof ThrowStatement || ASTNode instanceof VariableDeclaration))) {// || node instanceof ExpressionStatement || node instanceof BreakStatement || node instanceof ContinueStatement || node instanceof ThrowStatement || node instanceof VariableDeclaration || node instanceof ReturnStatement || node instanceof SwitchCase)) {
			return;
		}


		if (ASTNode instanceof FunctionNode) {
			isLongMethod();
			hasManyParameters();
		}


		if (ASTNode instanceof SwitchCase) {
			isLongMethod();
			hasManyParameters();
		}

		
		// JSNOSE - Amin: switch case
		else if (ASTNode instanceof SwitchCase) {
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

		return;
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
		
}
