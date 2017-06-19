package objects;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Message implements java.io.Serializable {
	private String message;
	private Date date;
	private SimpleDateFormat sdf;
	
	public Message(String message, Date date) {
		this.message = message;
		this.date = date;
		this.sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm");
	}
	
	public String getMessage() {
		return this.message;
	}
	
	public String getDate() {
		return sdf.format(date);
	}
}
