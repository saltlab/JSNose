package com.crawljax.examples;

import org.apache.commons.configuration.ConfigurationException;

import com.crawljax.browser.EmbeddedBrowser.BrowserType;
import com.crawljax.core.CrawljaxController;
import com.crawljax.core.CrawljaxException;
import com.crawljax.core.configuration.CrawlSpecification;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.configuration.InputSpecification;
import com.crawljax.core.configuration.ProxyConfiguration;
import com.crawljax.core.configuration.ThreadConfiguration;
import com.crawljax.plugins.aji.JSModifyProxyPlugin;
import com.crawljax.plugins.aji.executiontracer.AstInstrumenter;
import com.crawljax.plugins.webscarabwrapper.WebScarabWrapper;
import com.crawljax.core.configuration.Form;


/**
 * Simple Example.
 * 
 * @author dannyroest@gmail.com (Danny Roest)
 * @version $id$
 */
public final class CrawljaxSimpleExampleSettings {

	private static final String URL = "http://spci.st.ewi.tudelft.nl/demo/aowe/"; 
	//private static final String URL = "http://localhost/aowe/";  // from http://spci.st.ewi.tudelft.nl/demo/aowe/

		
	private static final int MAX_DEPTH = 0;
	private static final int MAX_NUMBER_STATES = 100;

	private CrawljaxSimpleExampleSettings() {
	}

	private static CrawljaxConfiguration getCrawljaxConfiguration() {
		CrawljaxConfiguration config = new CrawljaxConfiguration();
		config.setCrawlSpecification(getCrawlSpecification());
		config.setThreadConfiguration(getThreadConfiguration());
		// config.setBrowser(BrowserType.android);
		config.setBrowser(BrowserType.firefox);
		
			
		//config.setBrowser(BrowserType.chrome);
		//System.setProperty("webdriver.chrome.driver" , "....put address of chromedriver here....");
		
		return config;
	}

	private static ThreadConfiguration getThreadConfiguration() {
		ThreadConfiguration tc = new ThreadConfiguration();
		tc.setBrowserBooting(true);
		tc.setNumberBrowsers(1);
		tc.setNumberThreads(1);
		return tc;
	}

	private static CrawlSpecification getCrawlSpecification() {
		CrawlSpecification crawler = new CrawlSpecification(URL);

		//crawler.setMaximumRuntime(300);
		
		//crawler.setClickOnce(false);  // false: multiple click, true: click only once on each clickable

		// click these elements
		boolean tudu = false; // Amin: set tudu=true only if URL = "http://localhost:8080/tudu-dwr/";

		if (!tudu){
			// default order of clicks on candidate clickable
			crawler.clickDefaultElements();
			crawler.click("a");
			crawler.click("div");
			crawler.click("span");
			crawler.click("img");
			crawler.click("input").withAttribute("type", "submit");
			crawler.click("td");
		}else{
			// this is just for the TuduList application
			Form form=new Form();
			Form addList=new Form();
			form.field("j_username").setValue("amin");
			form.field("j_password").setValue("aminpass");
			form.field("dueDate").setValue("10/10/2010");
			form.field("priority").setValue("10");
			//addList.field("description").setValue("test");
			InputSpecification input = new InputSpecification();
			input.setValuesInForm(form).beforeClickElement("input").withAttribute("type", "submit");
			input.setValuesInForm(addList).beforeClickElement("a").withAttribute("href", "javascript:addTodo();");
			crawler.setInputSpecification(input);
			crawler.click("a");
			crawler.click("img").withAttribute("id", "add_trigger_calendar");
			crawler.click("img").withAttribute("id", "edit_trigger_calendar");
			
			//crawler.click("a");
			crawler.click("div");
			crawler.click("span");
			crawler.click("img");
			//crawler.click("input").withAttribute("type", "submit");
			crawler.click("td");

			crawler.dontClick("a").withAttribute("title", "My info");
			crawler.dontClick("a").withAttribute("title", "Log out");
			crawler.dontClick("a").withAttribute("text", "Cancel");
		}


		// except these
		crawler.dontClick("a").underXPath("//DIV[@id='guser']");
		crawler.dontClick("a").withText("Language Tools");
		
		if (!tudu)
			crawler.setInputSpecification(getInputSpecification());

		// limit the crawling scope
		crawler.setMaximumStates(MAX_NUMBER_STATES);
		crawler.setDepth(MAX_DEPTH);

		return crawler;
	}

	private static InputSpecification getInputSpecification() {
		InputSpecification input = new InputSpecification();
		input.field("q").setValue("Crawljax");
		return input;
	}

	/**
	 * @param args
	 *            the command line args
	 */
	public static void main(String[] args) {
		try {
			CrawljaxController crawljax = new CrawljaxController(getCrawljaxConfiguration());
			crawljax.run();
		} catch (CrawljaxException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (ConfigurationException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

}
