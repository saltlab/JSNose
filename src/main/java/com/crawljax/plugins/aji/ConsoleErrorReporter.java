package com.crawljax.plugins.aji;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

public class ConsoleErrorReporter implements ErrorReporter {
	public ConsoleErrorReporter() { }
	public EvaluatorException runtimeError(String message, String sourceName,
            int line, String lineSource,
            int lineOffset) {
		EvaluatorException ee = null;
		System.out.println("------------" + message + "------------");
		return ee;
	}
	
	public void warning(String message, String sourceName, int line,
                 String lineSource, int lineOffset) {
		System.out.println("------------" + message + "------------");
	}
	
    public void error(String message, String sourceName, int line,
            String lineSource, int lineOffset) {
    	System.out.println("------------" + message + "------------");
    }
}

