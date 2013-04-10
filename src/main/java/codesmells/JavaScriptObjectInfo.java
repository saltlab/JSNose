package codesmells;

import java.util.ArrayList;

public class JavaScriptObjectInfo {
	private String name = "";
	private String type = "";
	private String prototype = "";
	private int ASTdepth = 0;
	
	private ArrayList<String> usedPropetries = new ArrayList<String>();
	private ArrayList<String> ownPropetries = new ArrayList<String>();
	private ArrayList<String> inheritedPropetries = new ArrayList<String>();
	
	public JavaScriptObjectInfo(String name, int ASTNodeDepth){
		this.name = name;
		this.ASTdepth = ASTNodeDepth;
	}

	public void setType(String type){
		this.type = type;
	}
	
	
	public void addUsedProperty(String p){
		if (!usedPropetries.contains(p))
			usedPropetries.add(p);
	}
	
	public void addOwnProperty(String p){
		if (!ownPropetries.contains(p))
			ownPropetries.add(p);
	}
	
	public void addInheritedPropetries(String p){
		if (!inheritedPropetries.contains(p))
			inheritedPropetries.add(p);
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


}
