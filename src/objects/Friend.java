package objects;

import java.awt.Component;
import java.io.Serializable;

import javax.swing.JList;
import javax.swing.ListCellRenderer;

public class Friend implements Serializable{
	
	private String nick;
	private String username;
	private	String sex;
	
	public Friend(String username, String nick, String sex) {
		this.username = username;
		this.nick = nick;
		this.sex = sex;
	}
	
	public String getUserName() {
		return username;
	}
	
	public String getNick() {
		return nick;
	}
	
	public String getSex() {
		return sex;
	}
	
}
