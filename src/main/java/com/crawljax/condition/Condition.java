/**
 * 
 */
package com.crawljax.condition;

import com.crawljax.browser.EmbeddedBrowser;

import net.jcip.annotations.ThreadSafe;

import org.w3c.dom.NodeList;

/**
 * A condition is a condition which can be tested on the current state in the browser.
 *
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: Condition.java 396 2010-07-27 09:16:28Z slenselink@google.com $
 */
@ThreadSafe
public interface Condition {

	/**
	 * @param browser
	 *            The browser.
	 * @return whether the evaluated condition is satisfied
	 */
	boolean check(EmbeddedBrowser browser);

	/**
	 * @return the affected nodes (can be null)
	 */
	NodeList getAffectedNodes();
}
