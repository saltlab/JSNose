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

import org.json.JSONArray;
import org.json.JSONException;

import com.crawljax.core.CrawljaxException;
import com.crawljax.util.Helper;

/**
 * Representation of a Daikon variable.
 * 
 * @author Frank Groeneveld <frankgroeneveld@gmail.com>
 */
public class Variable {
	private String name;
	private String type;
	private boolean array;
	private Variable enclosingVariable;

	/**
	 * Construct a Daikon variable representation.
	 * 
	 * @param name
	 *            Name of the variable.
	 * @param type
	 *            Type of the variable.
	 * @param array
	 *            Whether the variable is an array.
	 * @param enclosingVariable
	 *            The enclosing variable.
	 */
	public Variable(String name, String type, boolean array, Variable enclosingVariable) {
		this.name = name;
		this.type = type;
		this.array = array;
		this.enclosingVariable = enclosingVariable;
	}

	/**
	 * Create a representation of value in Daikon trace file format.
	 * 
	 * @param value
	 *            The value to output in correct format.
	 * @return Daikon representation.
	 * @throws CrawljaxException
	 *             When the type is unsupported.
	 * @throws JSONException
	 */
	private String getValue(Object value) throws CrawljaxException, JSONException {
		if (isArray() && value instanceof JSONArray) {
			return getArray((JSONArray) value, type);
		} else {
			return getValue(value, type);
		}
	}

	/**
	 * Create a representation of value in Daikon trace file format.
	 * 
	 * @param value
	 *            The value to output in correct format.
	 * @param type
	 *            Type of the value.
	 * @return Daikon representation.
	 * @throws CrawljaxException
	 *             When the type is unsupported.
	 */
	private String getValue(Object value, String type) throws CrawljaxException {
		if (value == null) {
			return "null";
		}
		if (type.equals("string")) {
			/* make sure it fits on 1 line by removing new line chars */
			value = Helper.removeNewLines(value.toString());
			/* escape quotes */
			value = ((String) value).replaceAll("\\\"", "\\\\\"");
			return "\"" + value.toString() + "\"";

		} else if (type.equals("number")) {
			return value.toString();

		} else if (type.equals("boolean")) {
			if (value.toString().equals("true")) {
				return "1";
			} else {
				return "0";
			}
		} else if (type.equals("object")) {
			return "\"" + value.toString() + "\"";
		}

		throw new CrawljaxException("Unhandled type when converting to trace file " + type);
	}

	/**
	 * Traverse an array and create a representation in Daikon trace file format.
	 * 
	 * @param array
	 *            The list of elements.
	 * @param type
	 *            Type of the elements.
	 * @return Daikon representation of the list.
	 * @throws CrawljaxException
	 *             When type is not supported.
	 * @throws JSONException
	 */
	private String getArray(JSONArray array, String type) throws CrawljaxException, JSONException {
		String result = "[";

		for (int i = 0; i < array.length(); i++) {
			if (i != 0) {
				result += " ";
			}
			result += getValue(array.get(i), type);
		}
		return result + "]";
	}

	/**
	 * Daikon declaration of a variable.
	 * 
	 * @return Declaration string.
	 * @throws CrawljaxException
	 *             If type is unsupported.
	 */
	String getDeclaration() throws CrawljaxException {
		StringBuffer varDecl = new StringBuffer();

		if (isArray()) {
			varDecl.append("\tvariable " + name + "[..]\n");
			varDecl.append("\t\tvar-kind array\n\t\tarray 1\n");
			varDecl.append("\t\tenclosing-var " + getEnclosingVariable().getName() + "\n");
		} else {
			varDecl.append("\tvariable " + name + "\n");
			varDecl.append("\t\tvar-kind field " + name + "\n");
		}
		varDecl.append("\t\tdec-type " + type + "\n");
		varDecl.append("\t\trep-type ");

		if (type.equals("string")) {
			varDecl.append("java.lang.String");
		} else if (type.equals("boolean")) {
			varDecl.append("boolean");
		} else if (type.equals("undefined") || type.equals("function") || type.equals("object")
		        || type.equals("pointer")) {
			/*
			 * for undefined, declare as hashcode. this doesn't really matter because it was
			 * apparently never assigned a value. (at least we did not log a value).
			 */
			varDecl.append("hashcode");
		} else if (type.equals("number")) {
			/* number might be int or double. for now use double to be sure */
			varDecl.append("double");
		} else {
			throw new CrawljaxException("Unhandled type: " + type);
		}

		if (isArray()) {
			varDecl.append("[]");
		}
		varDecl.append("\n");

		return varDecl.toString();
	}

	/**
	 * Parses a JSON object into a Variable object.
	 * 
	 * @param var
	 *            The JSON object.
	 * @return The variable object.
	 * @throws JSONException
	 *             On error.
	 */
	public static Variable parse(JSONArray var) throws JSONException {
		/* retrieve the three values from the array */
		String name = var.getString(0);
		String type = (String) var.getString(1);
		Object value;
		try {
			value = var.getJSONArray(2);
		} catch (JSONException e) {
			value = var.getString(2);
			/* make sure it fits on 1 line by removing new line chars */
			value = Helper.removeNewLines((String) value);
			/* escape quotes */
			value = ((String) value).replaceAll("\\\"", "\\\\\"");
		}

		if (type.endsWith("_array")) {

			type = type.replaceAll("_array", "");

			Variable enclosingVariable = new Variable(name, "pointer", false, null);

			return new Variable(name, type, true, enclosingVariable);
		} else {
			return new Variable(name, type, false, null);
		}
	}

	/**
	 * Return the Daikon data trace record for this variable.
	 * 
	 * @param value
	 *            Value to put in there.
	 * @return Data trace record.
	 * @throws CrawljaxException
	 *             When type is not supported.
	 * @throws JSONException
	 *             On error.
	 */
	public String getData(Object value) throws CrawljaxException, JSONException {
		/*FROLIN'S CODE*/
		/*
		if (value.toString().equals("undefined")) {
			return this.toString() + "\nundefined\n2\n";
		}
		if (value.toString().equals("null")) {
			return this.toString() + "\nnull\n2\n";
		}
		*/
		/*END FROLIN'S CODE*/
		if (value.equals("undefined") || value.toString().equals("null")
		        || type.equals("undefined") || type.equals("object") || type.equals("function")
		        || type.equals("pointer")) {
			return this.toString() + "\nnonsensical\n2\n";
		}
		return this.toString() + "\n" + getValue(value) + "\n1\n";
	}

	/**
	 * @return The name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return The type.
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return The array.
	 */
	public boolean isArray() {
		return array;
	}

	/**
	 * @return The enclosingVariable.
	 */
	public Variable getEnclosingVariable() {
		return enclosingVariable;
	}

	@Override
	public String toString() {
		String localName = name;
		if (isArray()) {
			localName += "[..]";
		}
		return localName;
	}
}
