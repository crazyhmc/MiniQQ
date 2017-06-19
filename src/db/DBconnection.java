package db;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DBconnection {
	private String s;
	
	public DBconnection() {
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			e.printStackTrace();
		}	 
	}
	
	// 与数据库建立连接
	public Connection getConnection(String db, String username, String password) {
		try {
			return DriverManager.getConnection("jdbc:mysql://localhost:3306/" + db, username, password);
		} catch (SQLException e) {
			e.printStackTrace();
		} 
		return null;
	}
	
	// 与数据库断开连接
	public void close(Connection con, PreparedStatement state, ResultSet result) {
		if(result != null) {
			try {
				result.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			result = null;
		}
		
		if(state != null) {
			try {
				state.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			state = null;
		}
		
		if(con != null) {
			try {
				con.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			con = null;
		}
	}
	
	private void test() {
		
	}
	public static void main(String[] args) {
		DBconnection db = new DBconnection();
		db.test();
	}
	
}
