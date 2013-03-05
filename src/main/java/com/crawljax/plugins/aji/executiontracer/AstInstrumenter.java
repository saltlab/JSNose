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
package com.crawljax.plugins.aji.executiontracer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.Symbol;

import com.crawljax.plugins.aji.JSASTModifier;
import com.crawljax.util.Helper;

/**
 * This class is used to visit all JS nodes. When a node matches a certain condition, this class
 * will add instrumentation code near this code.
 * 
 * @author Frank Groeneveld
 * @author Amin Milani Fard
 * @version $Id: AstInstrumenter.java 6162 2009-12-16 13:56:21Z frank $
 */
public class AstInstrumenter extends JSASTModifier {

	private int instrumentedLinesCounter = 0;
	
	public AstInstrumenter() {
		super();
	}

	/**
	 * This will be added to the begining of the script
	 * 
	 * @return The AstNode which contains array.
	 */
	private AstNode jsLineExectutionCounter() {

		String code = "var " + jsName + "_counter = new Array(); " +
				"for (var i=0;i<" + instrumentedLinesCounter + ";i++)" +
				"if("+jsName + "_counter[i]== undefined || "+jsName + "_counter[i]== null) "+jsName + "_counter[i]=0;";
		
		// Amin: instrumentedLinesCounter resets to 0 for the next codes
		instrumentedLinesCounter = 0;
		//System.out.println(code);
		
		return parse(code);
	}

	@Override // instrumenting within a function
	protected AstNode createNode(FunctionNode function, String postfix, int lineNo) {
		String name = getFunctionName(function);

		// Amin: Adds instrumentation code
		String code = jsName + "_counter[" + Integer.toString(instrumentedLinesCounter) + "]++;";
		instrumentedLinesCounter++;
		
		return parse(code);
	}

	@Override// instrumenting out of function
	protected AstNode createNode(AstRoot root, String postfix, int lineNo, int rootCount) {

		// Amin: Adds instrumentation code
		 String code = jsName + "_counter[" + Integer.toString(instrumentedLinesCounter) + "]++;";
		instrumentedLinesCounter++;

		return parse(code);
	}

	@Override
	public void finish(AstRoot node) {
		/* add initialization code for the count of executed lines array */
		node.addChildToFront(jsLineExectutionCounter());
		// Amin: instrumentedLinesCounter resets to 0 for the next codes
		instrumentedLinesCounter = 0;
	}

	@Override
	public void start() {
		// Amin: just to be sure that index start from 0
		instrumentedLinesCounter = 0;
	}	
}
