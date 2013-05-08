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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.log4j.Logger;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.ast.Symbol;
import org.owasp.webscarab.httpclient.HTTPClient;
import org.owasp.webscarab.model.Request;
import org.owasp.webscarab.model.Response;
import org.owasp.webscarab.plugin.proxy.ProxyPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import codesmells.SmellDetector;

import com.crawljax.browser.EmbeddedBrowser;
//import com.crawljax.plugins.aji.executiontracer.JSExecutionTracer;
import com.crawljax.util.Helper;
import com.crawljax.util.Tree;
import com.crawljax.util.TreeNode;
import com.crawljax.util.TreeEditDist.LblTree;

import java.io.*;

/**
 * The JSInstrument proxy plugin adds instrumentation code to JavaScript files.
 * 
 * @author Frank Groeneveld
 * @version $Id: JSModifyProxyPlugin.java 6161 2009-12-16 13:47:15Z frank $
 */
public class JSModifyProxyPlugin extends ProxyPlugin {

	private static final Logger LOGGER = Logger.getLogger(JSModifyProxyPlugin.class.getName());

	private List<String> excludeFilenamePatterns;

	private final JSASTModifier modifier;
	
	private boolean htmlFound = false;
		
	// Amin: this is needed for retrieving the corresponding array
	private static List<String> modifiedJS;
	

	// Amin: keep track of event handlers
	private HashSet<Node> eventList1 = new HashSet<Node>();
	private HashSet<String> jsInTag = new HashSet<String>();
	private boolean foundTagsWithJS = false;
	
	private FileWriter fstream;
	private BufferedWriter out;
	
	public static List<String> getModifiedJSList(){
		return modifiedJS;
	}
	
	
	/**
	 * Construct without patterns.
	 * 
	 * @param modify
	 *            The JSASTModifier to run over all JavaScript.
	 */
	public JSModifyProxyPlugin(JSASTModifier modify) {
		excludeFilenamePatterns = new ArrayList<String>();
		modifiedJS = new ArrayList<String>();
		modifier = modify;
	}

	/**
	 * Constructor with patterns.
	 * 
	 * @param modify
	 *            The JSASTModifier to run over all JavaScript.
	 * @param excludes
	 *            List with variable patterns to exclude.
	 */
	public JSModifyProxyPlugin(JSASTModifier modify, List<String> excludes) {
		excludeFilenamePatterns = excludes;

		modifier = modify;
	}

	/**
	 * Adds some defaults to the list of files that should be excluded from modification. These
	 * include:
	 * <ul>
	 * <li>jQuery</li>
	 * <li>Prototype</li>
	 * <li>Scriptaculous</li>
	 * <li>MooTools</li>
	 * <li>Dojo</li>
	 * <li>YUI</li>
	 * <li>All kinds of Google scripts (Adwords, Analytics, etc)</li>
	 * <li>Minified JavaScript files with min, compressed or pack in the URL.</li>
	 * </ul>
	 */
	public void excludeDefaults() {
		
		excludeFilenamePatterns.add(".*jquery[-0-9.]*.js?.*");
		excludeFilenamePatterns.add(".*jquery.*.js?.*");
		excludeFilenamePatterns.add(".*prototype.*js?.*");
		excludeFilenamePatterns.add(".*scriptaculous.*.js?.*");
		excludeFilenamePatterns.add(".*mootools.js?.*");
		excludeFilenamePatterns.add(".*dojo.xd.js?.*");
		excludeFilenamePatterns.add(".*yuiloader.js?.*");
		excludeFilenamePatterns.add(".*google.*");
		//excludeFilenamePatterns.add(".*min.*.js?.*");
		excludeFilenamePatterns.add(".*pack.*.js?.*");
		excludeFilenamePatterns.add(".*compressed.*.js?.*");
		excludeFilenamePatterns.add(".*rpc.*.js?.*");
		excludeFilenamePatterns.add(".*o9dKSTNLPEg.*.js?.*");
		excludeFilenamePatterns.add(".*gdn6pnx.*.js?.*");
		excludeFilenamePatterns.add(".*show_ads.*.js?.*");
		excludeFilenamePatterns.add(".*ga.*.js?.*");

		//exclude list for tudu
		excludeFilenamePatterns.add(".*builder.js");
		excludeFilenamePatterns.add(".*controls.js");
		excludeFilenamePatterns.add(".*dragdrop.js");
		excludeFilenamePatterns.add(".*effects.js");
		excludeFilenamePatterns.add(".*prototype.js");
		excludeFilenamePatterns.add(".*scriptaculous.js");
		excludeFilenamePatterns.add(".*slider.js");
		excludeFilenamePatterns.add(".*unittest.js");
		excludeFilenamePatterns.add(".*engine.js");
		excludeFilenamePatterns.add(".*util.js");
		
		//exclude for collegesvis
		excludeFilenamePatterns.add(".*raphael.min.js");
	}

	@Override
	public String getPluginName() {
		return "JSInstrumentPlugin";
	}

	@Override
	public HTTPClient getProxyPlugin(HTTPClient in) {
		return new Plugin(in);
	}
	

	private boolean shouldModify(String name) {
		/* try all patterns and if 1 matches, return false */
		for (String pattern : excludeFilenamePatterns) {
			if (name.matches(pattern)) {
				LOGGER.info("Not modifying response for " + name);
				return false;
			}
		}

		LOGGER.info("Modifying response for " + name);

		return true;
	}

	/**
	 * This method tries to add instrumentation code to the input it receives. The original input is
	 * returned if we can't parse the input correctly (which might have to do with the fact that the
	 * input is no JavaScript because the server uses a wrong Content-Type header for JSON data)
	 * 
	 * @param input
	 *            The JavaScript to be modified
	 * @param scopename
	 *            Name of the current scope (filename mostly)
	 * @return The modified JavaScript
	 */
	private synchronized String modifyJS(String input, String scopename) {
		if (!shouldModify(scopename)) {
			return input;
		}
		try {

			String jsName = getJSName(scopename);
			
			SmellDetector.setJSName(jsName);

	
			/**
			 * Analysing inline javascript smell
			 */
			if (scopename.contains("script")){
				//System.out.println("scopename : " + getJSName(scopename));
				SmellDetector.analyseCoupling(jsName, input, jsInTag);
			}else if (foundTagsWithJS){
				SmellDetector.analyseCoupling("main_html", "", jsInTag);
			}

			
			
			AstRoot ast = null;

			/* initialize JavaScript context */
			Context cx = Context.enter();
			//cx.setErrorReporter(new ConsoleErrorReporter());

			/* create a new parser */
			//CompilerEnvirons ce = new CompilerEnvirons();
			//ce.setErrorReporter(new ConsoleErrorReporter());
			Parser rhinoParser = new Parser(new CompilerEnvirons(), cx.getErrorReporter());
			//Parser rhinoParser = new Parser(ce, cx.getErrorReporter());

			/* parse some script and save it in AST */
			ast = rhinoParser.parse(new String(input), scopename, 0);
			
			//System.out.println(ast.debugPrint());
			
			//System.out.println(makeTreeString(ast.debugPrint()));
			
			//LblTree lt1 = LblTree.fromString(makeTreeString(ast.debugPrint())); 
			
			//lt1.prettyPrint();
			
			//makeTreeString(ast.debugPrint());
			
			
			/*Print out AST root to file*/
			/*START*/
			//rootCounter++;
			/*try {
				System.out.println("writing on " + this.jsSourceOutputFolder);
				File file = new File(this.jsSourceOutputFolder + "/root" + rootCounter + ".js");
				if (!file.exists()) {
					file.createNewFile();
				}
				
				FileOutputStream fop = new FileOutputStream(file);
				
				fop.write(input.getBytes());
				fop.flush();
				fop.close();
			}
			catch (IOException ioe) {
				System.out.println("IO Exception");
			}*/
			/*END*/
			

			//System.out.println("AST BEFORE : ");
			//System.out.println(ast.toSource());
			
			modifier.setScopeName(scopename);

			modifier.start();


//			System.out.println("PRINTING AST ROOT");
//			for (Symbol s: ast.getSymbols()){
//				int sType = s.getDeclType();
//			    if (sType == Token.LP || sType == Token.VAR || sType == Token.LET || sType == Token.CONST){
//			    	System.out.println("s.getName() : " + s.getName());
//			    }
//			}

			
			/* recurse through AST and statically analayze the code for smells*/
			ast.visit(modifier);

			
			SmellDetector.generateReport(false);
			

			/*
			 *  Printing the instrumented code to a file
			 */
			try {
				//System.out.println("printing ast " + ast.toSource());
				File file = new File(jsName + ".txt");
				if (!file.exists()) {
					file.createNewFile();
				}

				FileOutputStream fop = new FileOutputStream(file);

				fop.write(ast.toSource().getBytes());
				fop.flush();
				fop.close();
			}
			catch (IOException ioe) {
				LOGGER.info("Could not write the instrumented file into disk!");
			}


			//if (htmlFound == true) {
			modifier.finish(ast);

			//Amin: add to the list of instrumented JS
			//if (!modifiedJS.contains(modifier.getJSName()) && modifier.getJSName().endsWith("_js"))
			if (!modifiedJS.contains(modifier.getJSName()))
				modifiedJS.add(modifier.getJSName());

			htmlFound = false;
			//}

			/* clean up */
			Context.exit();
			
						
			return ast.toSource();
		} catch (RhinoException re) {
			System.err.println(re.getMessage());
			LOGGER.warn("Unable to instrument. This might be a JSON response sent"
			        + " with the wrong Content-Type or a syntax error.");
		} catch (IllegalArgumentException iae) {
			LOGGER.warn("Invalid operator exception catched. Not instrumenting code.");
		}
		//LOGGER.warn("Here is the corresponding buffer: \n" + input + "\n");

		return input;
	}

	
	
	// Amin: making a string representation for ast
	private String makeTreeString(String astDebugFormat) {
		String result = "";
		String tempRead = "";
		String objName = "";

		int consecutiveGETPROP = 0;  // to count length of a message chain by counting GETPROPs
		int spaceCount = 0, newDepth = 0, prevDepth = -1, openBrackets = 0;
		for (int i=0; i<astDebugFormat.length(); i++){  //finding the next node (at any level)
			if (astDebugFormat.charAt(i) == '\t'){		// found next element at position i
				for (int j=i+1; j<astDebugFormat.length(); j++){  //finding the level
					if (astDebugFormat.charAt(j) == ' ')
						spaceCount++;
					else{
						newDepth = spaceCount/2;
						spaceCount = 0;
						for (int b=prevDepth-newDepth; b>=0 ; b--){ // adding "}" as much as depth change
							result+="}";
							openBrackets--;
						}
						result+="{";
						openBrackets++;
						while (astDebugFormat.charAt(j) != ' '){ // adding node type to the result string
							tempRead+=astDebugFormat.charAt(j);
							j++;
						}

					
						if (tempRead.equals("NAME")){
							// A NAME IS FOUND!
							// read two numbers and then read the name
							// TODO: this part is very messy! should be clean later
							j++;
							while (astDebugFormat.charAt(j) != ' ') // skipping numbers
								j++;
							j++;
							while (astDebugFormat.charAt(j) != ' ') // skipping numbers
								j++;
							j++;
							objName = "";
							while (astDebugFormat.charAt(j) != '\n'){ // adding node type to the result string
								objName+=astDebugFormat.charAt(j);
								j++;
							}
							
							//objName has the identifier name
							
						}
						
						result += tempRead;
						tempRead="";
						break;
					}				
				}
				prevDepth = newDepth;
			}
		}
		
		
		for (int i=0;i<openBrackets;i++)
			result+="}";
		
		System.out.println(result);
		
				
		return result;
	}


	
	// Amin: making a string representation for ast
	private void makeTree(String astDebugFormat) {
		
		Tree<String> t = new Tree<String>();
		TreeNode<String> n = new TreeNode<String>();
		n.setData("root");
		t.setRootElement(n);

		TreeNode<String> n2 = new TreeNode<String>();
		n2.setData("ch1");
		n.addChild(n2);
		n.addChild(n2);
		
		System.out.println(t.toString());
		
		
		String result = "";
		int spaceCount = 0, newDepth = 0, prevDepth = -1, openBrackets = 0;
		for (int i=0; i<astDebugFormat.length(); i++){  //finding the next node (at any level)
			if (astDebugFormat.charAt(i) == '\t'){		// found next element at position i
				for (int j=i+1; j<astDebugFormat.length(); j++){  //finding the level
					if (astDebugFormat.charAt(j) == ' ')
						spaceCount++;
					else{
						newDepth = spaceCount/2;
						spaceCount = 0;
						for (int b=prevDepth-newDepth; b>=0 ; b--){ // adding "}" as much as depth change
							result+="}";
							openBrackets--;
						}
						result+="{";
						openBrackets++;
						while (astDebugFormat.charAt(j) != ' '){ // adding node type to the result string
							result+=astDebugFormat.charAt(j);
							j++;
						}
						break;
					}				
				}
				prevDepth = newDepth;
			}
		}
		
		
	}

	
	
	
	//Amin: This is used to name the array which stores execution count for the scope in URL 
	private String getJSName(String URL) {
		int index = URL.lastIndexOf('/');
		String s = URL.substring(index+1, URL.length());
		String finalString = s.replace('.', '_');
		return finalString;
	}

	/**
	 * This method modifies the response to a request.
	 * 
	 * @param response
	 *            The response.
	 * @param request
	 *            The request.
	 * @return The modified response.
	 */
	private Response createResponse(Response response, Request request) {
		String type = response.getHeader("Content-Type");

		if (request.getURL().toString().contains("?thisisanexecutiontracingcall")) {
			LOGGER.info("Execution trace request " + request.getURL().toString());
			//JSExecutionTracer.addPoint(new String(request.getContent()));
			return response;
		}

		if (type != null && type.contains("javascript")) {
			/* instrument the code if possible */
			response.setContent(modifyJS(new String(response.getContent()), request.getURL().toString()).getBytes());
		} else if (type != null && type.contains("html")) {
			if (response.getStatus().equals("200")) {  // if response is status 200, it is was a bad attempt
				htmlFound = true;
			}
			try {
				Document dom = Helper.getDocument(new String(response.getContent()));
				
				
				/* Amin: finding event handlers */
				// checking a, div, span, img, input, td
				String[] tags = { "a", "div", "span", "img", "input", "td", "button"};  
				
				NodeList eventable = null;
				for (int i=0; i<tags.length; i++){
					eventable = dom.getElementsByTagName(tags[i]);
					checkEventHandler(eventable);
				}
				
				
				
				/* find script nodes in the html */
				NodeList nodes = dom.getElementsByTagName("script");
				
				for (int i = 0; i < nodes.getLength(); i++) {
					Node nType = nodes.item(i).getAttributes().getNamedItem("type");
					/* instrument if this is a JavaScript node */
					if ((nType != null && nType.getTextContent() != null && nType
					        .getTextContent().toLowerCase().contains("javascript"))) {
						String content = nodes.item(i).getTextContent();
						if (content.length() > 0) {
							String js = modifyJS(content, request.getURL() + "script" + i);
							nodes.item(i).setTextContent(js);
							continue;
						}
					}

					/* also check for the less used language="javascript" type tag */
					nType = nodes.item(i).getAttributes().getNamedItem("language");
					if ((nType != null && nType.getTextContent() != null && nType
					        .getTextContent().toLowerCase().contains("javascript"))) {
						String content = nodes.item(i).getTextContent();
						if (content.length() > 0) {
							String js = modifyJS(content, request.getURL() + "script" + i);
							nodes.item(i).setTextContent(js);
						}

					}
					
					/* also consider javascript in tags if there is no <script> */
					if (jsInTag.size() > 0)
						foundTagsWithJS = true;
					
					
				}
				
				
				/* only modify content when we did modify anything */
				if (nodes.getLength() > 0) {
					/* set the new content */
					response.setContent(Helper.getDocumentToByteArray(dom));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		/* return the response to the webbrowser */
		return response;
	}

	// Amin
	private void checkEventHandler(NodeList eventable) {

		String[] attributes = { "onclick",	"ondblclick", "onmouseover", "onmouseup", "onmousedown", "onmouseout", "onkeydown", "onkeypress" };  

		Node foundAttribute = null;
		for (int i = 0; i < attributes.length; i++) {
			for (int j = 0; j < eventable.getLength(); j++) {
				foundAttribute = eventable.item(j).getAttributes().getNamedItem(attributes[i]);
				if ((foundAttribute != null && foundAttribute.getTextContent() != null)){
					String tag = eventable.item(j).getNodeName() + " ";
					Node c = eventable.item(j).getAttributes().getNamedItem("class");
					if (c!=null)
						tag += c.toString()+ " ";
					tag += foundAttribute.toString();
					//System.out.println("tag: " + tag);
					jsInTag.add(tag);
					//System.out.println("jsInTag.size(): " + jsInTag.size());
				}


			}				
		}


	}

	/**
	 * WebScarab plugin that adds instrumentation code.
	 * 
	 * @author Frank Groeneveld
	 */
	private class Plugin implements HTTPClient {

		private HTTPClient client = null;

		/**
		 * Constructor for this plugin.
		 * 
		 * @param in
		 *            The HTTPClient connection.
		 */
		public Plugin(HTTPClient in) {
			client = in;
		}

		@Override
		public Response fetchResponse(Request request) throws IOException {
			Response response = client.fetchResponse(request);

			return createResponse(response, request);
		}
	}

}
