package server;

import java.sql.Connection;

import db.DBconnection;

public class QQServer {

	private LoginServer login;
	private FileServer file;
	private RegisterServer register;
	private DBconnection db;
	private Connection con;
	
	private QQServer() {
		db = new DBconnection();
		con = db.getConnection("qq", "root", "980420");
		login = new LoginServer(con);
		register = new RegisterServer(con);
		file = new FileServer();
	}
	
	private void open_close() {
		new Thread(() -> login.ac()).start();
		new Thread(() -> register.serve()).start();
		new Thread(() -> file.chatAccept()).start();
	}
	
	public static void main(String[] args) {
		new QQServer().open_close();
	}

}
