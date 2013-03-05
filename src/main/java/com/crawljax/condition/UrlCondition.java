package com.crawljax.condition;

import net.jcip.annotations.Immutable;

import com.crawljax.browser.EmbeddedBrowser;

/**
 * Conditions that returns true iff the browser's current url contains url. Note: Case insesitive
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $Id: UrlCondition.java 396 2010-07-27 09:16:28Z slenselink@google.com $
 */
@Immutable
public class UrlCondition extends AbstractCondition {

	private final String url;

	/**
	 * @param url
	 *            the URL.
	 */
	public UrlCondition(String url) {
		this.url = url;
	}

	@Override
	public boolean check(EmbeddedBrowser browser) {
		return browser.getCurrentUrl().toLowerCase().contains(url);
	}

}
