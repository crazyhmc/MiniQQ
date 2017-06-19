package objects;

public class User implements java.io.Serializable{

	private String password;
	private String nick;
	private String username;
	private	String sex;
	
	public User(String username, String password, String nick, String sex) {
		this.username = username;
		this.password = password;
		this.nick = nick;
		this.sex = sex;
	}
	
	public String getUserName() {
		return username;
	}
	
	public String getPassword() {
		return password;
	}
	
	public String getNick() {
		return nick;
	}
	
	public String getSex() {
		return sex;
	}
}
