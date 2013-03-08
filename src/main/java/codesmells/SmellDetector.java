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
import org.mozilla.javascript.ast.*;


public class SmellDetector {

	// JSNose parameters for smell detection
	private static final int MAX_METHID_LENGTH = 20;
	private static final int MAX_NUMBERS_OF_PARAMETERS = 4;

	
	private AstNode ASTNode = null;
	
	
	public SmellDetector(AstNode node) {
		ASTNode = node;
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
	public void analyseAST() {
		
		
		if (!((ASTNode instanceof FunctionNode || ASTNode instanceof ReturnStatement || ASTNode instanceof SwitchCase || 
				ASTNode instanceof AstRoot || ASTNode instanceof ExpressionStatement || ASTNode instanceof BreakStatement || 
				ASTNode instanceof ContinueStatement || ASTNode instanceof ThrowStatement || ASTNode instanceof VariableDeclaration))) {// || node instanceof ExpressionStatement || node instanceof BreakStatement || node instanceof ContinueStatement || node instanceof ThrowStatement || node instanceof VariableDeclaration || node instanceof ReturnStatement || node instanceof SwitchCase)) {
			return;
		}


		if (ASTNode instanceof FunctionNode) {
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
	

	
	public void findFunctions(String input, String scopename) {
		try {
			AstRoot ast = null;

			/* initialize JavaScript context */
			Context cx = Context.enter();

			/* create a new parser */
			Parser rhinoParser = new Parser(new CompilerEnvirons(), cx.getErrorReporter());

			/* parse some script and save it in AST */
			ast = rhinoParser.parse(new String(input), scopename, 0);
			
			/*Look for instances of "function" in input then figure out where it ends*/
			
			String inputCopy = input;
			
			int indexOfFuncString = inputCopy.indexOf("function ");
			while (indexOfFuncString != -1) {
				String sub = inputCopy.substring(indexOfFuncString);
				int nextOpenParen = sub.indexOf("(");
				String funcName = sub.substring(9, nextOpenParen); //"function " has 9 characters
				
				int firstOpenBrace = sub.indexOf("{");
				int countOpenBraces = 1;
				int countCloseBraces = 0;
				
				int endIndex = firstOpenBrace;
				while (countOpenBraces != countCloseBraces) {
					endIndex++;
					if (sub.charAt(endIndex) == '{') {
						countOpenBraces++;
					}
					else if (sub.charAt(endIndex) == '}') {
						countCloseBraces++;
					}
				}
				
				String code = sub.substring(0, endIndex+1);
				
				//System.out.println(" CODE IS " + code);
				
				try {
					File file = new File(funcName + ".js");
					if (!file.exists()) {
						file.createNewFile();
					}
					
					FileOutputStream fop = new FileOutputStream(file);
					
					fop.write(code.getBytes());
					fop.flush();
					fop.close();
				}
				catch (IOException ioe) {
					System.out.println("IO Exception");
				}
				
				inputCopy = sub.substring(endIndex+1);
				indexOfFuncString = inputCopy.indexOf("function ");
			}


			System.out.println("AST : ");
			System.out.println(ast.toSource());

		} catch (RhinoException re) {
			System.err.println(re.getMessage());
			//LOGGER.warn("Unable to instrument. This might be a JSON response sent"
			//        + " with the wrong Content-Type or a syntax error.");
		} catch (IllegalArgumentException iae) {
			//LOGGER.warn("Invalid operator exception catched. Not instrumenting code.");
		}
		//LOGGER.warn("Here is the corresponding buffer: \n" + input + "\n");
	}

	
	
}
