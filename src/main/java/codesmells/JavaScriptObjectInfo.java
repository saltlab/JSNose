package codesmells;

import java.util.ArrayList;

public class JavaScriptObjectInfo {
	private String name = "";
	private String type = "";
	private String prototype = "";
	private int ASTdepth = 0;
	
	//	usedInheritedPropetries = intersection of ownPropetries and inheritedPropetries 
	//	usedInheritedPropetries= inheritedPropetries - ownPropetries
	private ArrayList<String> ownPropetries = new ArrayList<String>();
	private ArrayList<String> inheritedPropetries = new ArrayList<String>();
	private ArrayList<String> usedInheritedPropetries = new ArrayList<String>();	// Inherited properties used or override
	private ArrayList<String> notUsedInheritedPropetries = new ArrayList<String>();	// Inherited properties not used or override

	private ArrayList<String> usedPropetries = new ArrayList<String>();	// Properties which are used (in the right hand side) which might be own or inherited

	
	public JavaScriptObjectInfo(String name, int ASTNodeDepth){
		this.name = name;
		this.ASTdepth = ASTNodeDepth;
	}

	public void setType(String type){
		this.type = type;
	}
	
	public void addOwnProperty(String p){
		if (!ownPropetries.contains(p))
			ownPropetries.add(p);
	}
	
	public void addInheritedPropetries(String p){
		if (!inheritedPropetries.contains(p))
			inheritedPropetries.add(p);
	}

	public void addUsedProperty(String p){
		if (!usedPropetries.contains(p))
			usedPropetries.add(p);
	}
	
	public void setPrototype(String p){
		prototype = p;
	}
	

	public String getName(){
		return name;
	}


	public int getASTDepth(){
		return ASTdepth;
	}
	
	public String toString(){
		String objInfoString = "Object name:" + name + "\nOwn properties:" + ownPropetries +"\nPrototype object: " + prototype +"\n" ;
		return objInfoString;
	}


	public ArrayList<String> getOwnPropetries() {
		return ownPropetries;
	}

	public void setOwnPropetries(ArrayList<String> ownPropetries) {
		this.ownPropetries = ownPropetries;
	}

	public ArrayList<String> getInheritedPropetries() {
		return inheritedPropetries;
	}

	public void setInheritedPropetries(ArrayList<String> inheritedPropetries) {
		this.inheritedPropetries = inheritedPropetries;
	}

	public ArrayList<String> getUsedInheritedPropetries() {
		return usedInheritedPropetries;
	}

	public void setUsedInheritedPropetries(ArrayList<String> usedInheritedPropetries) {
		this.usedInheritedPropetries = usedInheritedPropetries;
	}

	public ArrayList<String> getNotUsedInheritedPropetries() {
		return notUsedInheritedPropetries;
	}

	public void setNotUsedInheritedPropetries(
			ArrayList<String> notUsedInheritedPropetries) {
		this.notUsedInheritedPropetries = notUsedInheritedPropetries;
	}

	
	public ArrayList<String> getUsedPropetries() {
		return usedPropetries;
	}

	public void setUsedPropetries(ArrayList<String> usedPropetries) {
		this.usedPropetries = usedPropetries;
	}
}
