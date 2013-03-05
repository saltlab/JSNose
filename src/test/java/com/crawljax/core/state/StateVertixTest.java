/**
 * Created Dec 19, 2007
 */
package com.crawljax.core.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.crawljax.core.state.Eventable.EventType;

/**
 * TODO: DOCUMENT ME!
 * 
 * @author mesbah
 * @version $Id: StateVertixTest.java 198 2010-01-30 02:45:54Z amesbah $
 */
public class StateVertixTest {
	private StateVertix s;
	private String name;
	private String dom;

	@Before
	public void setUp() throws Exception {
		name = "index";
		dom = "<body></body>";
		s = new StateVertix(name, dom, null);
	}

	/**
	 * Test method for {@link com.crawljax.core.state.StateVertix#hashCode()}.
	 */
	@Test
	public void testHashCode() {
		StateVertix state = new StateVertix("foo", dom, null);
		StateVertix temp = new StateVertix(name, dom, null);

		assertEquals(temp.hashCode(), state.hashCode());
	}

	/**
	 * Test method for {@link com.crawljax.core.state.StateVertix#StateVertix(java.lang.String)}.
	 */
	@Test
	public void testStateVertixString() {
		StateVertix sv = new StateVertix(name, "", null);
		assertNotNull(sv);
	}

	@Test
	public void testStateVertixStringString() {
		assertNotNull(s);
	}

	/**
	 * Test method for {@link com.crawljax.core.state.StateVertix#getName()}.
	 */
	@Test
	public void testGetName() {
		assertEquals(name, s.getName());
	}

	/**
	 * Test method for {@link com.crawljax.core.state.StateVertix#getDom()}.
	 */
	@Test
	public void testGetDom() {
		assertEquals(dom, s.getDom());
		// assertEquals(newDom, s.getDomJtidied());
	}

	/**
	 * Test method for {@link com.crawljax.core.state.StateVertix#equals(java.lang.Object)}.
	 */
	@Test
	public void testEqualsObject() {
		StateVertix stateEqual = new StateVertix("foo", dom, null);
		StateVertix stateNotEqual = new StateVertix("foo", "<table><div>bla</div</table>", null);
		StateVertix sv = new StateVertix(name, dom, null);
		assertTrue(stateEqual.equals(sv));

		assertFalse(stateNotEqual.equals(sv));

		assertFalse(stateEqual.equals(null));
		assertFalse(stateEqual.equals(new Eventable(new Identification(Identification.How.xpath,
		        "/body/div[3]/a"), EventType.click)));

		sv.setGuidedCrawling(true);
		assertFalse(stateEqual.equals(sv));
	}

	/**
	 * Test method for {@link com.crawljax.core.state.StateVertix#toString()}.
	 */
	@Test
	public void testToString() {
		assertNotNull(s.toString());
	}

	@Test
	public void testGetDomSize() {
		String HTML =
		        "<SCRIPT src='js/jquery-1.2.1.js' type='text/javascript'></SCRIPT> "
		                + "<SCRIPT src='js/jquery-1.2.3.js' type='text/javascript'></SCRIPT>"
		                + "<body><div id='firstdiv' class='orange'></div><div><span id='thespan'>"
		                + "<a id='thea'>test</a></span></div></body>";
		StateVertix sv = new StateVertix("test", HTML, null);

		int count = sv.getDomSize();
		assertEquals(242, count);
	}
}
