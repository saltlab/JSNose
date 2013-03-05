/*
    Automatic JavaScript Invariants is a plugin for Crawljax that can be
    used to derive JavaScript invariants automatically and use them for
    regressions testing.
    Copyright (C) 2010  crawljax.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/
package com.crawljax.plugins.aji;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.*;

import com.crawljax.core.CrawljaxController;
import com.crawljax.plugins.aji.executiontracer.ProgramPoint;

/**
 * Abstract class that is used to define the interface and some functionality for the NodeVisitors
 * that modify JavaScript.
 * 
 * @author Frank Groeneveld
 * @version $Id: JSASTModifier.java 6161 2009-12-16 13:47:15Z frank $
 */
public abstract class JSASTModifier implements NodeVisitor {

	private final Map<String, String> mapper = new HashMap<String, String>();

	protected static final Logger LOGGER = Logger.getLogger(CrawljaxController.class.getName());
	
	/**
	 * This is used by the JavaScript node creation functions that follow.
	 */
	private CompilerEnvirons compilerEnvirons = new CompilerEnvirons();

	/**
	 * Contains the scopename of the AST we are visiting. Generally this will be the filename
	 */
	private String scopeName = null;

	//Added by Amin to store js corresponding name
	protected String jsName = null;
	
	/**
	 * @param scopeName
	 *            the scopeName to set
	 */
	public void setScopeName(String scopeName) {
		this.scopeName = scopeName;

		//Amin: This is used to name the array which stores execution count for the scope in URL 
		int index = scopeName.lastIndexOf('/');
		String s = scopeName.substring(index+1, scopeName.length());
		jsName = s.replace('.', '_');
	}
	
	/**
	 * @return the jsName
	 */
	public String getJSName() {
		return jsName;
	}
	
	/**
	 * @return the scopeName
	 */
	public String getScopeName() {
		return scopeName;
	}

	/**
	 * Abstract constructor to initialize the mapper variable.
	 */
	/**
	 * Abstract constructor to initialize the mapper variable.
	 */
	protected JSASTModifier() {
		/* add -<number of arguments> to also make sure number of arguments is the same */
		mapper.put("addClass", "attr('class')");
		mapper.put("removeClass", "attr('class')");
		mapper.put("css-2", "css(%0)");
		mapper.put("attr-2", "attr(%0)");
		mapper.put("prop-2", "attr(%0)");
		mapper.put("append", "text()");
		mapper.put("after", "parent().html()");
		mapper.put("appendTo", "html()");
		mapper.put("before","parent().html()");
		mapper.put("detach", "html()");
		mapper.put("remove", "html()");
		mapper.put("empty", "html()");
		mapper.put("height-1", "height()");
		mapper.put("width-1", "width()");
		mapper.put("insertBefore", "prev().html()");
		mapper.put("insertAfter", "next().html()");
		mapper.put("offset-1", "offset()");
		mapper.put("prepend", "html()");
		mapper.put("prependTo", "html()");
		mapper.put("html-1", "html()");
		mapper.put("setAttribute-2", "getAttribute(%0)");
		mapper.put("text-1", "text()");
	//	mapper.put("className", "className");
		
		mapper.put("addClass", "attr('class')");
		mapper.put("removeClass", "attr('class')");
		mapper.put("css-2", "css(%0)");
		mapper.put("attr-2", "attr(%0)");
		mapper.put("append", "html()");
	}
	
	
	/**
	 * Parse some JavaScript to a simple AST.
	 * 
	 * @param code
	 *            The JavaScript source code to parse.
	 * @return The AST node.
	 */
	public AstNode parse(String code) {
		//Parser p = new Parser(compilerEnvirons, null);
		compilerEnvirons.setErrorReporter(new ConsoleErrorReporter());
		Parser p = new Parser(compilerEnvirons, new ConsoleErrorReporter());

		//System.out.print(code+"*******\n");

		return p.parse(code, null, 0);
	}

	/**
	 * Find out the function name of a certain node and return "anonymous" if it's an anonymous
	 * function.
	 * 
	 * @param f
	 *            The function node.
	 * @return The function name.
	 */
	protected String getFunctionName(FunctionNode f) {
		Name functionName = f.getFunctionName();

		if (functionName == null) {
			return "anonymous" + f.getLineno();
		} else {
			return functionName.toSource();
		}
	}

	/**
	 * Creates a node that can be inserted at a certain point in function.
	 * 
	 * @param function
	 *            The function that will enclose the node.
	 * @param postfix
	 *            The postfix function name (enter/exit).
	 * @param lineNo
	 *            Linenumber where the node will be inserted.
	 * @return The new node.
	 */
	protected abstract AstNode createNode(FunctionNode function, String postfix, int lineNo);
	
	/**
	 * Create a new block node with two children.
	 * 
	 * @param node
	 *            The child.
	 * @return The new block.
	 */
	private Block createBlockWithNode(AstNode node) {
		Block b = new Block();

		b.addChild(node);

		return b;
	}

	/**
	 * @param node
	 *            The node we want to have wrapped.
	 * @return The (new) node parent (the block probably)
	 */
	private AstNode makeSureBlockExistsAround(AstNode node) {
		AstNode parent = node.getParent();

		if (parent instanceof IfStatement) {
			/* the parent is an if and there are no braces, so we should make a new block */
			IfStatement i = (IfStatement) parent;

			/* replace the if or the then, depending on what the current node is */
			if (i.getThenPart().equals(node)) {
				i.setThenPart(createBlockWithNode(node));
			} else {
				i.setElsePart(createBlockWithNode(node));
			}
		} else if (parent instanceof WhileLoop) {
			/* the parent is a while and there are no braces, so we should make a new block */
			/* I don't think you can find this in the real world, but just to be sure */
			WhileLoop w = (WhileLoop) parent;
			w.setBody(createBlockWithNode(node));
		} else if (parent instanceof ForLoop) {
			/* the parent is a for and there are no braces, so we should make a new block */
			/* I don't think you can find this in the real world, but just to be sure */
			ForLoop f = (ForLoop) parent;
			f.setBody(createBlockWithNode(node));
		}
		// else if (parent instanceof SwitchCase) {
		// SwitchCase s = (SwitchCase) parent;
		// List<AstNode> statements = new TreeList(s.getStatements());

		// for (int i = 0; i < statements.size(); i++) {
		// if (statements.get(i).equals(node)) {
		// statements.add(i, newNode);

		// s.setStatements(statements);
		// break;
		// }
		// }

		// }
		return node.getParent();
	}

	private AstNode getLineNode(AstNode node) {
		while ((!(node instanceof ExpressionStatement) && !(node instanceof Assignment))
		        || node.getParent() instanceof ReturnStatement) {
			node = node.getParent();
		}
		return node;
	}
	
	/**
	 * Creates a node that can be inserted at a certain point in the AST root.
	 * Changed by Amin
	 * 
	 * @param root
	 * 			The AST root that will enclose the node.
	 * @param postfix
	 * 			The postfix name.
	 * @param lineNo
	 * 			Linenumber where the node will be inserted.
	 * @param rootCount
	 * 			Unique integer that identifies the AstRoot
	 * @return The new node
	 */
	protected abstract AstNode createNode(AstRoot root, String postfix, int lineNo, int rootCount);


	/**
	 * Actual visiting method.
	 * 
	 * @param node
	 *            The node that is currently visited.
	 * @return Whether to visit the children.
	 */
	//@Override
	public boolean visit(AstNode node) {
		FunctionNode func;
		
		if (!((node instanceof FunctionNode || node instanceof ReturnStatement || node instanceof SwitchCase || node instanceof AstRoot || node instanceof ExpressionStatement || node instanceof BreakStatement || node instanceof ContinueStatement || node instanceof ThrowStatement || node instanceof VariableDeclaration))) {// || node instanceof ExpressionStatement || node instanceof BreakStatement || node instanceof ContinueStatement || node instanceof ThrowStatement || node instanceof VariableDeclaration || node instanceof ReturnStatement || node instanceof SwitchCase)) {
			return true;
		}

		if (node instanceof FunctionNode) {
			func = (FunctionNode) node;

			/* this is function enter */
			AstNode newNode = createNode(func, ProgramPoint.ENTERPOSTFIX, func.getLineno());

			func.getBody().addChildToFront(newNode);
			
			node = (AstNode) func.getBody().getFirstChild();
			node = (AstNode) node.getNext(); //The first node is the node just added in front, so get next node
			int firstLine = 0;
			if (node != null) {
				firstLine = node.getLineno();
			}

			/* get last line of the function */
			node = (AstNode) func.getBody().getLastChild();
			/* if this is not a return statement, we need to add logging here also */
			if (!(node instanceof ReturnStatement)) {
				AstNode newNode_end = createNode(func, ProgramPoint.EXITPOSTFIX, node.getLineno()-firstLine+1);
				/* add as last statement */
				func.getBody().addChildToBack(newNode_end);
			}			
			//System.out.println(func.toSource());
		}
		else if (node instanceof AstRoot) {
			AstRoot rt = (AstRoot) node;
			
			if (rt.getSourceName() == null) { //make sure this is an actual AstRoot, not one we created
				return true;
			}
			
			//this is the entry point of the AST root
			m_rootCount++;
			AstNode newNode = createNode(rt, ProgramPoint.ENTERPOSTFIX, rt.getLineno(), m_rootCount);

			rt.addChildToFront(newNode);
			
			node = (AstNode) rt.getFirstChild();
			node = (AstNode) node.getNext(); //The first node is the node just added in front, so get next node
			int firstLine = 0;
			if (node != null) {
				firstLine = node.getLineno();
			}
			
			// get last line of the function
			node = (AstNode) rt.getLastChild();
			//if this is not a return statement, we need to add logging here also
			if (!(node instanceof ReturnStatement)) {
				AstNode newNode_end = createNode(rt, ProgramPoint.EXITPOSTFIX, node.getLineno()-firstLine+1, m_rootCount);
				//add as last statement
				rt.addChildToBack(newNode_end);
			}
		}
		//else if (node instanceof BreakStatement || node instanceof ConditionalExpression || node instanceof ContinueStatement || node instanceof ExpressionStatement || node instanceof FunctionCall || node instanceof Assignment || node instanceof InfixExpression || node instanceof ThrowStatement || node instanceof UnaryExpression || node instanceof VariableDeclaration || node instanceof VariableInitializer || node instanceof XmlDotQuery || node instanceof XmlMemberGet || node instanceof XmlPropRef || node instanceof Yield) {
		else if (node instanceof ExpressionStatement || node instanceof BreakStatement || node instanceof ContinueStatement || node instanceof ThrowStatement || node instanceof VariableDeclaration) {
			if (node instanceof VariableDeclaration) {
				//Make sure this variable declaration is not part of a for loop
				if (node.getParent() instanceof ForLoop) {
					return true;
				}
			}
			
			//Make sure additional try statement is not instrumented
			if (node instanceof TryStatement) {
				return true; //no need to add instrumentation before try statement anyway since we only instrument what's inside the blocks
			}
			
			func = node.getEnclosingFunction();
			
			if (func != null) {
				AstNode firstLine_node = (AstNode) func.getBody().getFirstChild();
				if (func instanceof FunctionNode && firstLine_node instanceof IfStatement) { //Perform extra check due to addition if statement
					firstLine_node = (AstNode) firstLine_node.getNext();
				}
				if (func instanceof FunctionNode && firstLine_node instanceof TryStatement) {
					TryStatement firstLine_node_try = (TryStatement) firstLine_node;
					firstLine_node = (AstNode) firstLine_node_try.getTryBlock().getFirstChild();
				}
				firstLine_node = (AstNode) firstLine_node.getNext();
				int firstLine = 0;
				if (firstLine_node != null) {
					//If first child is an ExpressionStatement or VariableDeclaration, then there might be multiple instances of the instrumented node at the beginning of the FunctionNode's list of children
					while (firstLine_node != null) {
						firstLine = firstLine_node.getLineno();
						if (firstLine > 0) {
							break;
						}
						else {
							firstLine_node = (AstNode) firstLine_node.getNext();
						}
					}
				}
				
				if (node.getLineno() >= firstLine) {
					AstNode newNode = createNode(func, ":::INTERMEDIATE", node.getLineno()-firstLine+1);
					//AstNode parent = node.getParent();
					
					AstNode parent = makeSureBlockExistsAround(node);
					
					//parent.addChildAfter(newNode, node);
					try {
						parent.addChildBefore(newNode, node);
					}
					catch (NullPointerException npe) {
						//System.out.println("Could not addChildBefore!");
						//System.out.println(npe.getMessage());
					}
				}
			}
			else { //The expression must be outside a function
				AstRoot rt = node.getAstRoot();
				if (rt == null || rt.getSourceName() == null) {
					return true;
				}
				AstNode firstLine_node = (AstNode) rt.getFirstChild();
				//if (firstLine_node instanceof IfStatement) { //Perform extra check due to addition if statement
				//	firstLine_node = (AstNode) firstLine_node.getNext();
				//}
				if (firstLine_node instanceof Block) {
					firstLine_node = (AstNode)firstLine_node.getFirstChild(); //Try statement
				}
				if (firstLine_node instanceof TryStatement) {
					TryStatement firstLine_node_try = (TryStatement) firstLine_node;
					firstLine_node = (AstNode) firstLine_node_try.getTryBlock().getFirstChild();
				}
				firstLine_node = (AstNode) firstLine_node.getNext();
				int firstLine = 0;
				if (firstLine_node != null) {
					//If first child is an ExpressionStatement or VariableDeclaration, then there might be multiple instances of the instrumented node at the beginning of the FunctionNode's list of children
					while (firstLine_node != null) {
						firstLine = firstLine_node.getLineno();
						if (firstLine > 0) {
							break;
						}
						else {
							firstLine_node = (AstNode) firstLine_node.getNext();
						}
					}
				}
				
				if (node.getLineno() >= firstLine) {
					AstNode newNode = createNode(rt, ":::INTERMEDIATE", node.getLineno()-firstLine+1, m_rootCount);
					//AstNode parent = node.getParent();
					
					AstNode parent = makeSureBlockExistsAround(node);
					
					//parent.addChildAfter(newNode, node);
					try {
						parent.addChildBefore(newNode, node);
					}
					catch (NullPointerException npe) {
						System.out.println(npe.getMessage());
					}
				}
			}
		}
		else if (node instanceof ReturnStatement) {
			func = node.getEnclosingFunction();
			AstNode firstLine_node = (AstNode) func.getBody().getFirstChild();
			if (func instanceof FunctionNode && firstLine_node instanceof IfStatement) { //Perform extra check due to addition if statement
				firstLine_node = (AstNode) firstLine_node.getNext();
			}
			if (func instanceof FunctionNode && firstLine_node instanceof TryStatement) {
				TryStatement firstLine_node_try = (TryStatement) firstLine_node;
				firstLine_node = (AstNode) firstLine_node_try.getTryBlock().getFirstChild();
			}
			firstLine_node = (AstNode) firstLine_node.getNext();
			int firstLine = 0;
			if (firstLine_node != null) {
				//If first child is an ExpressionStatement or VariableDeclaration, then there might be multiple instances of the instrumented node at the beginning of the FunctionNode's list of children
				while (firstLine_node != null) {
					firstLine = firstLine_node.getLineno();
					if (firstLine > 0) {
						break;
					}
					else {
						firstLine_node = (AstNode) firstLine_node.getNext();
					}
				}
			}
			
			AstNode parent = makeSureBlockExistsAround(node);
			
			AstNode newNode = createNode(func, ProgramPoint.EXITPOSTFIX, node.getLineno()-firstLine+1);

			/* the parent is something we can prepend to */
			parent.addChildBefore(newNode, node);

		}
		else if (node instanceof SwitchCase) {
			//Add block around all statements in the switch case
			SwitchCase sc = (SwitchCase)node;
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

		/* have a look at the children of this node */
		return true;
	}

	/**
	 * This method is called when the complete AST has been traversed.
	 * 
	 * @param node
	 *            The AST root node.
	 * @param jsName
	 *            The javascript scope name.	 
	 */
	public abstract void finish(AstRoot node);

	/**
	 * This method is called before the AST is going to be traversed.
	 */
	public abstract void start();
	
	private int m_rootCount = 0;
}
