package com.crawljax.condition;

import net.jcip.annotations.Immutable;

import com.crawljax.browser.EmbeddedBrowser;

/**
 * A condition which returns true iff the expression does NOT occur in the DOM.
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: NotRegexCondition.java 396 2010-07-27 09:16:28Z slenselink@google.com $
 */
@Immutable
public class NotRegexCondition extends AbstractCondition {

	private final RegexCondition regexCondition;

	/**
	 * @param expression
	 *            the regular expression.
	 */
	public NotRegexCondition(String expression) {
		this.regexCondition = new RegexCondition(expression);
	}

	@Override
	public boolean check(EmbeddedBrowser browser) {
		return Logic.not(regexCondition).check(browser);
	}

}
