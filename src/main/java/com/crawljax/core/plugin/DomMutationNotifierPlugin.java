/**
 * 
 */
package com.crawljax.core.plugin;

import com.crawljax.browser.EmbeddedBrowser;

/**
 * A plugin interface to povide an extension point for performing a quick pre-test to by-pass a complete 
 * Dom comparison. Chances are that a pretest such as using Dom Mutation Summery concludes that the Dom has not
 * been changed.
 * 
 * @author alireza.aut@gmail.com, Alireza Zarei
 *
 *
 *Important: you must always set the DomMutationNotifierPluginCheck to true in your constructor.
 *
 * for example, suppose crawljaxConfiguration is your instance of CrawljaxConfiguration class then you must have the
 * following line in your constructor:
 *
 * 		crawljaxConfiguration.getCrawlSpecification().setDomMutationNotifierPlugin(true);

 */
public interface DomMutationNotifierPlugin extends Plugin {


	/**
	 * 
	 * Checks if dom has been changed. True means that the Complete Dom Check must be done. False means that
	 * Dom Has not been changed and the eloborate Dom comparison must be by-passed.
	 * @param parameter
	 *             needed parameter
	 */
	boolean hasDomMutated( EmbeddedBrowser browser);
}
