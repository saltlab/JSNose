package com.crawljax.condition;

import net.jcip.annotations.Immutable;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.core.state.Identification;

/**
 * Conditions that returns true iff element found with By is visible.
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: NotVisibleCondition.java 396 2010-07-27 09:16:28Z slenselink@google.com $
 */
@Immutable
public class NotVisibleCondition extends AbstractCondition {

	private final VisibleCondition visibleCondition;

	/**
	 * @param identification
	 *            the identification.
	 */
	public NotVisibleCondition(Identification identification) {
		this.visibleCondition = new VisibleCondition(identification);
	}

	@Override
	public boolean check(EmbeddedBrowser browser) {
		return Logic.not(visibleCondition).check(browser);
	}

}
