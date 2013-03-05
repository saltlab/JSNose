package com.crawljax.condition;

import com.crawljax.browser.EmbeddedBrowser;

import net.jcip.annotations.Immutable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Condition that returns true iff experssion occurs in the dom.
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: RegexCondition.java 446 2010-09-16 09:17:24Z slenselink@google.com $
 */
@Immutable
public class RegexCondition extends AbstractCondition {

	private final String expression;

	/**
	 * @param expression
	 *            the regular expression.
	 */
	public RegexCondition(String expression) {
		this.expression = expression;
	}

	@Override
	public boolean check(EmbeddedBrowser browser) {
		String dom = browser.getDom();
		Pattern p = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(dom);
		return m.find();
	}

}
