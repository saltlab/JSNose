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
 * Representation of a Daikon trace file.
 * 
 * @author Frank Groeneveld <frankgroeneveld@gmail.com>
 */
public class Trace {
	private ArrayList<ProgramPoint> programPoints;

	/**
	 * Construct a new Daikon trace representation.
	 */
	public Trace() {
		programPoints = new ArrayList<ProgramPoint>();
	}

	/**
	 * Get or create a program point defined by name.
	 * 
	 * @param name
	 *            Name of the program point.
	 * @return The ProgramPoint object.
	 */
	public ProgramPoint programPoint(String name) {
		for (ProgramPoint p : programPoints) {
			if (p.getName().equals(name)) {
				return p;
			}
		}

		ProgramPoint p = new ProgramPoint(name);
		programPoints.add(p);

		return p;
	}

	/**
	 * Parse JSON object into a trace.
	 * 
	 * @param jsonObject
	 *            The JSON object.
	 * @return The trace object.
	 * @throws JSONException
	 *             On error.
	 */
	public static Trace parse(JSONArray jsonObject) throws JSONException {
		Trace trace = new Trace();
		for (int j = 0; j < jsonObject.length(); j++) {
			JSONArray value = jsonObject.getJSONArray(j);

			String programPointName = value.getString(0);
			ProgramPoint prog = trace.programPoint(programPointName);

			String prefix = value.getString(1);

			prog.addPoint(prefix);

			value = value.getJSONArray(2);
			/* output all the variable values */
			for (int i = 0; i < value.length(); i++) {
				JSONArray o = value.getJSONArray(i);
				prog.variable(Variable.parse(o));

			}
		}

		return trace;
	}

	/**
	 * @return Daikon declaration string for the complete trace.
	 * @throws CrawljaxException
	 *             When an unsupported type is found.
	 */
	public String getDeclaration() throws CrawljaxException {
		StringBuffer result = new StringBuffer();

		result.append("decl-version 2.0\n");

		for (ProgramPoint p : programPoints) {
			result.append(p.getDeclaration());
		}

		return result.toString();
	}

	/**
	 * Returns all data trace records.
	 * 
	 * @param jsonObject
	 *            Raw trace object.
	 * @return The Daikon data trace records as a String.
	 * @throws CrawljaxException
	 *             When an unsupported type is encountered.
	 * @throws JSONException
	 *             On error.
	 */
	public String getData(JSONArray jsonObject) throws CrawljaxException, JSONException {
		StringBuffer result = new StringBuffer();

		for (int j = 0; j < jsonObject.length(); j++) {
			JSONArray value = jsonObject.getJSONArray(j);

			String programPointName = value.getString(0);
			ProgramPoint prog = programPoint(programPointName);

			String prefix = value.getString(1);

			result.append(prog.getData(prefix, value.getJSONArray(2)));

		}

		return result.toString();
	}
}
