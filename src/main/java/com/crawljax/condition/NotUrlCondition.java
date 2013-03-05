package com.crawljax.condition;

import net.jcip.annotations.Immutable;

import com.crawljax.browser.EmbeddedBrowser;

/**
 * Conditions that returns true iff the browser's current url NOT contains url. Note: Case
 * insensitive.
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: NotUrlCondition.java 396 2010-07-27 09:16:28Z slenselink@google.com $
 */
@Immutable
public class NotUrlCondition extends AbstractCondition {

	private final UrlCondition urlCondition;

	/**
	 * @param url
	 *            the URL.
	 */
	public NotUrlCondition(String url) {
		this.urlCondition = new UrlCondition(url);
	}

	@Override
	public boolean check(EmbeddedBrowser browser) {
		return Logic.not(urlCondition).check(browser);
	}

}
