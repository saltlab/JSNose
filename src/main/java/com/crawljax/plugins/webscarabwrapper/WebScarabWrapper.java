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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.owasp.webscarab.model.Preferences;
import org.owasp.webscarab.model.StoreException;
import org.owasp.webscarab.plugin.Framework;
import org.owasp.webscarab.plugin.proxy.Proxy;
import org.owasp.webscarab.plugin.proxy.ProxyPlugin;

import com.crawljax.core.configuration.ProxyConfiguration;
import com.crawljax.core.plugin.ProxyServerPlugin;

public class WebScarabWrapper implements ProxyServerPlugin {

	/* Logger is log4j, but java logging is also imported, so be clear about that here */
	private static final org.apache.log4j.Logger LOGGER =
	        org.apache.log4j.Logger.getLogger(WebScarabWrapper.class.getName());

	/**
	 * List of proxy plugins that should be added to the proxy before it is started.
	 */
	private List<ProxyPlugin> plugins = new ArrayList<ProxyPlugin>();

	/**
	 * The WebScarab HTTP proxy object this class is a wrapper for.
	 */
	private Proxy proxy;

	@Override
	public void proxyServer(ProxyConfiguration config) {
		try {
			startProxy(config);
			LOGGER.info("WebScarab proxy started");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Disables logging to the console normally done by WebScarab.
	 */
	private void disableConsoleLogging() {
		Logger l = Logger.getLogger("org.owasp.webscarab.plugin.Framework");
		l.setLevel(Level.OFF);
		l = Logger.getLogger("org.owasp.webscarab.httpclient.URLFetcher");
		l.setLevel(Level.OFF);
		l = Logger.getLogger("org.owasp.webscarab.plugin.proxy.Listener");
		l.setLevel(Level.OFF);

		/* disable all logging */
		l = Logger.getLogger("");
		l.setLevel(Level.OFF);
	}

	/**
	 * Start the HTTP proxy on the specified port. Also starts the request buffer plugin.
	 * 
	 * @param config
	 *            ProxyConfiguration object.
	 * @throws IOException
	 *             When error reading writing.
	 * @throws StoreException
	 *             When error storing preference.
	 */
	private void startProxy(ProxyConfiguration config) throws IOException, StoreException {
		disableConsoleLogging();

		Framework framework = new Framework();

		/* set listening port before creating the object to avoid warnings */
		Preferences.setPreference("Proxy.listeners", "127.0.0.1:" + config.getPort());

		this.proxy = new Proxy(framework);

		/* add the plugins to the proxy */
		for (ProxyPlugin p : plugins) {
			proxy.addPlugin(p);
		}

		framework.setSession("BlackHole", null, "");

		/* start the proxy */
		this.proxy.run();
	}

	/**
	 * Add a plugin to the proxy. IMPORTANT: call this before the proxy is actually started.
	 * 
	 * @param plugin
	 *            The plugin to add.
	 */
	public void addPlugin(ProxyPlugin plugin) {
		plugins.add(plugin);
	}
}
