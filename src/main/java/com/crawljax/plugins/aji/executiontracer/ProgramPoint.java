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

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;

import com.crawljax.core.CrawljaxException;

/**
 * Representation of a Daikon program point.
 * 
 * @author Frank Groeneveld <frankgroeneveld@gmail.com>
 */
public class ProgramPoint {

	public static final String ENTERPOSTFIX = ":::ENTER";
	public static final String EXITPOSTFIX = ":::EXIT";
	public static final String POINTPOSTFIX = ":::POINT";

	private String name;
	private ArrayList<Variable> variables;
	private ArrayList<String> points;

	/**
	 * Construct a new Daikon program point representation.
	 * 
	 * @param name
	 *            The name of the program point.
	 */
	public ProgramPoint(String name) {
		this.name = name;
		variables = new ArrayList<Variable>();
		points = new ArrayList<String>();
	}

	/**
	 * Add a point in this program point (like exit, entry etc) if it doesn't exist yet.
	 * 
	 * @param prefix
	 *            Prefix to add.
	 */
	public void addPoint(String prefix) {
		for (String point : points) {
			if (point.equals(prefix)) {
				return;
			}
		}

		points.add(prefix);
	}

	/**
	 * Add a variable declaration to this program point if it doesn't exist yet.
	 * 
	 * @param variable
	 *            The variable to add.
	 * @return The variable.
	 */
	public Variable variable(Variable variable) {
		for (Variable v : variables) {
			if (v.getName().equals(variable.getName()) && v.isArray() == variable.isArray()) {
				if (!v.getType().equals("undefined")) {
					return v;
				} else {
					variables.remove(v);
					break;
				}
			}
		}
		variables.add(variable);
		if (variable.getEnclosingVariable() != null) {
			variable(variable.getEnclosingVariable());
		}

		return variable;
	}

	/**
	 * Get variable by name.
	 * 
	 * @param name
	 *            The name of the variable.
	 * @return The variable.
	 */
	public Variable getVariable(String name) {
		for (Variable v : variables) {
			if (v.getName().equals(name)) {
				return v;
			}
		}

		return null;
	}

	/**
	 * @return The name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Output program point for all prefixes with variable declarations.
	 * 
	 * @return String containing Daikon variables declarations.
	 * @throws CrawljaxException
	 *             On undefined type.
	 */
	public String getDeclaration() throws CrawljaxException {

		StringBuffer varDecl = new StringBuffer();

		for (String prefix : points) {

			/* print program point */
			varDecl.append("ppt " + name + prefix + "\n");
			if (prefix.endsWith(ENTERPOSTFIX)) {
				varDecl.append("ppt-type enter\n");
			} else if (prefix.contains(POINTPOSTFIX)) {
				varDecl.append("ppt-type point\n");
			} else {
				/* else means it ends with something like :::EXIT123 */
				varDecl.append("ppt-type subexit\n");
			}

			/* print all variables for this program point */
			for (Variable v : variables) {
				varDecl.append(v.getDeclaration());
			}
			varDecl.append('\n');
		}

		return varDecl.toString();
	}

	/**
	 * Returns a Daikon trace record for this program point.
	 * 
	 * @param postfix
	 *            Prefix (such as :::ENTER).
	 * @param data
	 *            Data to put in there.
	 * @return Record as a string.
	 * @throws CrawljaxException
	 *             When an unsupported type is encountered.
	 * @throws JSONException
	 *             On error.
	 */
	public String getData(String postfix, JSONArray data) throws CrawljaxException, JSONException {
		StringBuffer result = new StringBuffer();
		boolean found = false;

		result.append(name + postfix + "\n");

		for (Variable var : variables) {
			for (int i = 0; i < data.length(); i++) {
				JSONArray item = data.getJSONArray(i);

				if (var.getName().equals(item.getString(0))) {
					result.append(var.getData(item.get(2)));
					found = true;
					break;
				}
			}
			if (!found) {
				result.append(var.getData("undefined"));
				found = true;
			}
		}

		result.append("\n");

		return result.toString();
	}
}
