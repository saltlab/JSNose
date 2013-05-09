package codesmells;

class FunctionInfo{

	String name;
	int numberOfParameters;
	int linesOfCode;
	int lineNumber;
	
	public FunctionInfo(String name, int numberOfParameters, int linesOfCode, int lineNumber) {
		super();
		this.name = name;
		this.numberOfParameters = numberOfParameters;
		this.linesOfCode = linesOfCode;
		this.lineNumber = lineNumber;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getNumberOfParameters() {
		return numberOfParameters;
	}
	public void setNumberOfParameters(int numberOfParameters) {
		this.numberOfParameters = numberOfParameters;
	}
	public int getLinesOfCode() {
		return linesOfCode;
	}
	public void setLinesOfCode(int linesOfCode) {
		this.linesOfCode = linesOfCode;
	}
	public int getLineNumber() {
		return lineNumber;
	}
	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}
	
}
