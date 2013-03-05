package com.crawljax.condition.browserwaiter;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.core.state.Identification;

import net.jcip.annotations.ThreadSafe;

/**
 * Checks whether an element is visible.
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: ExpectedVisibleCondition.java 440 2010-09-13 18:28:40Z slenselink@google.com $
 */
@ThreadSafe
public class ExpectedVisibleCondition implements ExpectedCondition {

	private final Identification identification;

	/**
	 * Constructor.
	 * 
	 * @param identification
	 *            identification to use.
	 */
	public ExpectedVisibleCondition(Identification identification) {
		this.identification = identification;
	}

	@Override
	public boolean isSatisfied(EmbeddedBrowser browser) {
		return browser.isVisible(identification);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + ": " + this.identification;
	}

}
