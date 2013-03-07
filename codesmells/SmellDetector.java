package codesmells;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ast.*;


public class SmellDetector {

	private AstNode ASTNode = null;
	private int m_rootCount = 0;
	
	
	public SmellDetector(AstNode node) {
		ASTNode = node;
	}


	/**
	 * Analysing abstract syntax tree for code smells.
	 * This is to the following smells:
	 * 1. long list of parameters
	 *
	 * 
	 * @param node
	 *            The AST node that is currently visited.
	 */
	public void analyseAST() {
		
		FunctionNode func;
		
		if (!((ASTNode instanceof FunctionNode || ASTNode instanceof ReturnStatement || ASTNode instanceof SwitchCase || 
				ASTNode instanceof AstRoot || ASTNode instanceof ExpressionStatement || ASTNode instanceof BreakStatement || 
				ASTNode instanceof ContinueStatement || ASTNode instanceof ThrowStatement || ASTNode instanceof VariableDeclaration))) {// || node instanceof ExpressionStatement || node instanceof BreakStatement || node instanceof ContinueStatement || node instanceof ThrowStatement || node instanceof VariableDeclaration || node instanceof ReturnStatement || node instanceof SwitchCase)) {
			return;
		}

		if (ASTNode instanceof FunctionNode) {
			func = (FunctionNode) ASTNode;
			// JSNOSE - Amin: long parameter list
			if (func.getParams().size() >= 0){
				System.out.println("function " + func.getName() + " has " + 
						func.getParams().size() + " parameters in line " + func.getLineno());
			
				System.out.println("also it has " + func.getParamCount());
			}
			
		}
		return;
	}


	
	
	
	
	private void findFunctions(String input, String scopename) {
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
