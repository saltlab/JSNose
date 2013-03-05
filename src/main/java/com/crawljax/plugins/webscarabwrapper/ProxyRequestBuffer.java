/*
	WebScarabWrapper is a plugin for Crawljax that starts and stops the
    WebScarab proxy at the correct moments.
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
package com.crawljax.plugins.webscarabwrapper;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.owasp.webscarab.httpclient.HTTPClient;
import org.owasp.webscarab.model.Request;
import org.owasp.webscarab.model.Response;
import org.owasp.webscarab.plugin.proxy.ProxyPlugin;

/**
 * This class is a plugin for the WebScarab proxy. 
 * It buffers outgoing HTTP requests. Two buffers are maintained, a local
 * one and a complete one. When the local request buffer
 * is fetched, the contents are copied and returned an the
 * local buffer is emptied. The complete buffer contains all requests made
 * since the start of Crawljax.
 * 
 * @author corpaul
 *
 */
public class ProxyRequestBuffer extends ProxyPlugin {

	/**
	 * The local buffer. Contains the requests since the last getRequestBuffer()
	 * call.
	 */
	private List<Request> buffer = new LinkedList<Request>();

	/**
	 * Complete buffer. Contains all the Requests since the start of the plugin.
	 */
	private List<Request> completeBuffer = new LinkedList<Request>();

	/**
	 * Set to true iff all requests should be buffered. This is used
	 * to get a list of all requests since the beginning of execution.
	 * Note: may require a large amount of memory.
	 */
	private boolean bufferAllRequests;

	/**
	 * Constructor.
	 * @param bufferAllRequests Whether to buffer all of the requests.
	 */
	public ProxyRequestBuffer(boolean bufferAllRequests) {
		this.buffer = new LinkedList<Request>();
		this.bufferAllRequests = bufferAllRequests;
		if (this.bufferAllRequests) {
			this.completeBuffer = new LinkedList<Request>();
		}
	}

	/**
	 * Copy the contents of the local buffer into a new buffer,
	 * return the new buffer and empty the local buffer.
	 * @return A copy of the local buffer
	 */
	public synchronized List<Request> getRequestBuffer() {
		List<Request> toReturn = new LinkedList<Request>();
		for (Request r : this.buffer) {
			toReturn.add(r);
		}
		clearBuffer();
		return toReturn;

	}

	/**
	 * Return the complete buffer.
	 * @return The complete buffer of all responses.
	 */
	public synchronized List<Request> getCompleteBuffer() {
		return this.completeBuffer;
	}

	/**
	 * Add request to the local and complete buffer.
	 * @param request the request to buffer
	 */
	public synchronized void bufferRequest(Request request) {
		this.buffer.add(request);
		if (this.bufferAllRequests) {
			this.completeBuffer.add(request);
		}
	}

	/**
	 * Empty the local buffer.
	 */
	public synchronized void clearBuffer() {
		this.buffer.clear();
	}

	/**
	 * The plugin name.
	 * @return The plugin name.
	 */
	@Override
	public String getPluginName() {
		return new String("Request Buffer");
	}

	@Override
	public HTTPClient getProxyPlugin(HTTPClient in) {
		return new Plugin(in);
	}

	/**
	 * The actual WebScarab plugin.
	 * @author Cor Paul
	 */
	private class Plugin implements HTTPClient {

		private HTTPClient client;

		/**
		 * Constructor.
		 * @param in HTTPClient
		 */
		public Plugin(HTTPClient in) {
			this.client = in;
		}

		/**
		 * Buffer the request.
		 * @param request The incoming request.
		 * @throws IOException on read write error.
		 * @return The response.
		 */
		public Response fetchResponse(Request request) throws IOException {
			bufferRequest(request);
			// response currently unused since we only care
			// about outgoing requests
			Response response = this.client.fetchResponse(request);
			return response;
		}

	}

}
