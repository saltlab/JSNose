package com.crawljax.condition;

import net.jcip.annotations.Immutable;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.core.state.Identification;

/**
 * Conditions that returns true iff element found with By is visible.
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: VisibleCondition.java 396 2010-07-27 09:16:28Z slenselink@google.com $
 */
@Immutable
public class VisibleCondition extends AbstractCondition {

	private final Identification identification;

	/**
	 * @param identification
	 *            the identification.
	 */
	public VisibleCondition(Identification identification) {
		this.identification = identification;
	}

	@Override
	public boolean check(EmbeddedBrowser browser) {
		return browser.isVisible(identification);
	}

}
