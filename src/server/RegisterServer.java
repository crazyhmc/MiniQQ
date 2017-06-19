package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import db.DBconnection;
import objects.User;

public class RegisterServer {
	private ServerSocket server;
	private Socket socket;
	
	private Connection con;
	private PreparedStatement state;
	private ResultSet res;
	
	public RegisterServer(Connection con) {
		try {
			server = new ServerSocket(8890);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.con = con;
	}
	
	public void serve() {
		while(true) {
			try {
				socket = server.accept();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(socket != null) {
				new Thread(new RegisterServe(socket)).start();
			}
		}
	}
	
	public void close() {
		
	}
	public static void main(String[] args) {
		
	}
	
	private class RegisterServe implements Runnable {
		private Socket s;
		private ObjectInputStream ois;
		private ObjectOutputStream oop;
		
		public RegisterServe(Socket s) {
			this.s = s;
		}
		
		@Override
		public void run() {
			String username = null;
			String password = null;
			String nick = null;
			String sex = null;
			User u = null;
			// 保证对数据库的操作不会被打断
			synchronized (con) {
				try {
					ois = new ObjectInputStream(s.getInputStream());
					oop = new ObjectOutputStream(s.getOutputStream());
					username = ois.readUTF();
					System.out.println(username);
					state = con.prepareStatement("select*from user where username = ?");

					state.setString(1, username);

					res = state.executeQuery();
					if (!res.next()) {
						oop.writeUTF("没毛病");
						oop.flush();
						u = (User)ois.readObject();
						username = u.getUserName();
						password = u.getPassword();
						nick = u.getNick();
						sex = u.getSex();

						state = con.prepareStatement("insert into user(username,password,nickname,sex) values(?,?,?,?)");
						state.setString(1, username);
						state.setString(2, password);
						state.setString(3, nick);
						state.setString(4, sex);
						if(!state.execute()){
							oop.writeUTF("注册成功");
							oop.flush();
						}
						else {
							oop.writeUTF("注册失败");
							oop.flush();
						}
					} else {
						oop.writeUTF("用户名已存在");
						oop.flush();
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} finally {
					if (res != null) {
						try {
							res.close();
						} catch (SQLException e) {
							e.printStackTrace();
						}
						res = null;
					}
					if (state != null) {
						try {
							state.close();
						} catch (SQLException e) {
							e.printStackTrace();
						}
						state = null;
					}
					if (ois != null) {
						try {
							ois.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						ois = null;
					}
					if (oop != null) {
						try {
							oop.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						oop = null;
					}
					if (s != null) {
						try {
							s.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						s = null;
					}
				}
			}
			
		}
	}
}
