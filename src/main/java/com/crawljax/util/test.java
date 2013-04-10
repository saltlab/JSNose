package com.crawljax.util;

public class test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Tree<String> t = new Tree<String>();
		TreeNode<String> n = new TreeNode<String>();
		n.setData("root");
		t.setRootElement(n);

		TreeNode<String> n2 = new TreeNode<String>();
		n2.setData("ch1");
		n.addChild(n2);
		n.addChild(n2);
		
		System.out.println(t.toString());
		
		
	}

}
