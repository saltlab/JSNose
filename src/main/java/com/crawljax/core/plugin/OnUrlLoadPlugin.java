package com.crawljax.core.plugin;

import com.crawljax.browser.EmbeddedBrowser;

/**
 * Plugin type that is called after the initial URL is (re)loaded. Example: refreshing the page
 * (clear the browser cache). The OnURLloadPlugins are run just after the Browser has gone to the
 * initial URL. Not only the first time but also every time the Core navigates back (back-tracking).
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: OnUrlLoadPlugin.java 396 2010-07-27 09:16:28Z slenselink@google.com $
 */
public interface OnUrlLoadPlugin extends Plugin {

	/**
	 * Method that is called after the url is (re) loaded. Warning: changing the browser can change
	 * the behaviour of Crawljax. It is not a copy!
	 * 
	 * @param browser
	 *            the current browser instance
	 */
	void onUrlLoad(EmbeddedBrowser browser);

}