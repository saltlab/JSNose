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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ast.AstRoot;
import org.owasp.webscarab.httpclient.HTTPClient;
import org.owasp.webscarab.model.Request;
import org.owasp.webscarab.model.Response;
import org.owasp.webscarab.plugin.proxy.ProxyPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

//import com.crawljax.plugins.aji.executiontracer.JSExecutionTracer;
import com.crawljax.util.Helper;

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
		
	private int rootCounter = 0;

	// Amin: this is needed for retrieving the corresponding array
	private static List<String> modifiedJS;
	
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
		/*excludeFilenamePatterns.add(".*jquery[-0-9.]*.js?.*");
		excludeFilenamePatterns.add(".*prototype.*js?.*");
		excludeFilenamePatterns.add(".*scriptaculous.*.js?.*");
		excludeFilenamePatterns.add(".*mootools.js?.*");
		excludeFilenamePatterns.add(".*dojo.xd.js?.*");
		excludeFilenamePatterns.add(".*yuiloader.js?.*");
		excludeFilenamePatterns.add(".*google.*");
		excludeFilenamePatterns.add(".*min.*.js?.*");
		excludeFilenamePatterns.add(".*pack.*.js?.*");
		excludeFilenamePatterns.add(".*compressed.*.js?.*");
		*/
		
		excludeFilenamePatterns.add(".*jquery[-0-9.]*.js?.*");
		excludeFilenamePatterns.add(".*jquery.*.js?.*");
		excludeFilenamePatterns.add(".*prototype.*js?.*");
		excludeFilenamePatterns.add(".*scriptaculous.*.js?.*");
		excludeFilenamePatterns.add(".*mootools.js?.*");
		excludeFilenamePatterns.add(".*dojo.xd.js?.*");
		excludeFilenamePatterns.add(".*yuiloader.js?.*");
		excludeFilenamePatterns.add(".*google.*");
		excludeFilenamePatterns.add(".*min.*.js?.*");
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
	//	excludeFilenamePatterns.add(".*engine.js");
		excludeFilenamePatterns.add(".*util.js");
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
			
			/*Print out AST root to file*/
			/*START*/
			rootCounter++;
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
			
			/*Look for instances of "function" in input then figure out where it ends*/
			/*START*/
		/*	String inputCopy = input;
			
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
					File file = new File(this.jsSourceOutputFolder + "/" + funcName + ".js");
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
			/*END*/

			//System.out.println("AST BEFORE : ");
			//System.out.println(ast.toSource());
			
			modifier.setScopeName(scopename);

			modifier.start();

			/* recurse through AST */
			ast.visit(modifier);

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
			
			
			//System.out.println("**** " + modifier.getJSName() + " AST AFTER : ");
			//System.out.println(ast.toSource());
			
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
			if (response.getStatus().equals("200")) {
				htmlFound = true;
			}
			try {
				Document dom = Helper.getDocument(new String(response.getContent()));
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
