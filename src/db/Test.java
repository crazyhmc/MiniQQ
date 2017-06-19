package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Test extends DBconnection {

	public static void main(String[] args) {
		DBconnection db = new DBconnection();
		Connection con = db.getConnection("qq", "root", "980420");
		ResultSet res = null;
		try {
			PreparedStatement state = con.prepareStatement("select nickname from user where id=1");
			res = state.executeQuery();
			Thread.sleep(1000);
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			if(res.next())
			System.out.println(res.getString(1));
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
	}

}
