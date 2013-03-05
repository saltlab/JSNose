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
public final class GuidedCrawljaxExampleSettings {

	//private static final String URL = "http://spci.st.ewi.tudelft.nl/demo/aowe/"; 
	
	//Final selected experimental objects
	private static final String URL = "http://127.0.0.1:8081/phormer331/"; // PhotoGallery
	//private static final String URL = "http://localhost/chess/index.html"; // chessGame
	//private static final String URL = "http://localhost:8080/tudu-dwr/";   // TuduList
	//private static final String URL = "http://127.0.0.1:8081/ajaxfilemanagerv_tinymce1.1/tinymce_test.php";  // TinyMCE


	//private static final String URL = "http://localhost/ptable/";  // PeriodicTable
	//private static final String URL = "http://localhost/artemis/ajaxtabscontent/ajaxtabscontent/demo.htm";
	//private static final String URL = "http://localhost/tinymce_3.5.7_jquery/%5Ctinymce/examples/";
	//private static final String URL = "http://localhost/5/";  //
	//private static final String URL = "http://localhost/aowe/";  // from http://spci.st.ewi.tudelft.nl/demo/aowe/
	//private static final String URL = "http://localhost/same-game/";  // SameGame
	//private static final String URL = "http://localhost/artemis/ball_pool/ball_pool/index.html";  // BallPool
	//private static final String URL = "http://127.0.0.1:8080/fractal_viewer/fractal_viewer/";  //FractalViewer
	//private static final String URL = "http://localhost/tictactoe2/tictacgame_Even_Smarter_3.htm";  //TicTacToe
	//private static final String URL = "http://localhost/listo/";  // AjaxList
	//private static final String URL = "http://127.0.0.1:8080/TinySiteXml/";  // Small Ajax site
	//private static final String URL = "http://localhost/ria/"; // from http://ssrg.eecs.uottawa.ca/TestRIA/

	
	//private static final int MAX_DEPTH = 3;
	private static final int MAX_DEPTH = 0; // this indicates no depth-limit
	
	//private static final int MAX_NUMBER_STATES = 50;  // for PhotoGallary
	//private static final int MAX_NUMBER_STATES = 15;  // for tudu
	//private static final int MAX_NUMBER_STATES = 25;  // for TinyMCE
	//private static final int MAX_NUMBER_STATES = 20;  // for Chessgame
	private static final int MAX_NUMBER_STATES = 10000;

	private GuidedCrawljaxExampleSettings() {

	}

	private static CrawljaxConfiguration getCrawljaxConfiguration() {
		CrawljaxConfiguration config = new CrawljaxConfiguration();
		config.setCrawlSpecification(getCrawlSpecification());
		config.setThreadConfiguration(getThreadConfiguration());
		// config.setBrowser(BrowserType.android);
		config.setBrowser(BrowserType.firefox);
		
			
		// Amin: Create a Proxy for the purpose of code instrumentation to measure js code coverage
		config.setProxyConfiguration(new ProxyConfiguration());
		WebScarabWrapper web = new WebScarabWrapper();
		config.addPlugin(web);
		JSModifyProxyPlugin modifier = new JSModifyProxyPlugin(new AstInstrumenter());
		modifier.excludeDefaults();
		web.addPlugin(modifier);
		
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

		// crawler.setMaximumRuntime(300); 		
		
		crawler.setDiverseCrawling(false);    // this is the default setting
		crawler.setEfficientCrawling(false);  // this is the default setting

		// crawler.setDiverseCrawling(true);   // do guided crawling
		// crawler.setClickOnce(false);       // false: multiple click, true: click only once on each clickable

		boolean doEfficientCrawling = false;
		boolean doRandomEventExec = false;   // set it true for randomizing event execution
		
		if (doRandomEventExec){
			crawler.setRandomEventExec(true);
		}else if (doEfficientCrawling){
			crawler.setEfficientCrawling(true);
			crawler.setClickOnce(false);
		}

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
