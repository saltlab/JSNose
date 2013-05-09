package codesmells;

/**
 * SmellLocation is used to keep the location of a code smell in a JavaScript file
 * 
 * @author Amin Milani Fard
 */
public class SmellLocation{
	String smellyItemName;
	String jsFile;
	int lineNumber;
	
	public SmellLocation(String smellyItemName, String jsFile, int lineNumber){
		this.smellyItemName = smellyItemName;
		this.jsFile = jsFile;
		this.lineNumber = lineNumber;
	}
	
	public String getJsFile() {
		return jsFile;
	}
	public void setJsFile(String jsFile) {
		this.jsFile = jsFile;
	}
	public int getLineNumber() {
		return lineNumber;
	}
	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}
	public String getSmellyItemName() {
		return smellyItemName;
	}

	public void setSmellyItemName(String smellyItemName) {
		this.smellyItemName = smellyItemName;
	}	
    
	@Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SmellLocation)) return false;

        SmellLocation sl = (SmellLocation) o;

        if ((this.jsFile == null) ? (sl.jsFile != null) : !this.jsFile.equals(sl.jsFile)) {  
            return false;  
        }  
        if (this.lineNumber != sl.lineNumber) {  
            return false;  
        } 
        return true;
    }

    @Override
    public int hashCode() {
        return 1;
    }
    	
}
