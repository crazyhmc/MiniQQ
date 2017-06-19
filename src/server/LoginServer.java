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
import java.util.Enumeration;
import java.util.Vector;

import objects.Friend;
import objects.Message;
import objects.MultiChatInfo;
import objects.User;

public class LoginServer {

	private ServerSocket server;
	private PreparedStatement statement;
	private Connection con;
	private ResultSet res;
	// 当前在线用户
	private Vector<Friend> curFr;
	private Vector<LoginServe> vector;

	// 多人聊天相关
	private static int multichat_id = 0;
	private Vector<MultiChatInfo> multiChatInfos;
	
	public LoginServer(Connection con) {
		try {
			server = new ServerSocket(8888);
			this.con = con;
			this.curFr = new Vector<>();
			this.vector = new Vector<>();
			this.multiChatInfos = new Vector<>();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	void ac() {
		while(true) {
			Socket socket = null;
			try {
				socket = server.accept();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(socket != null) {
				// 创建新的线程
				LoginServe temp = new LoginServe(socket);
				temp.start();
			}
		}
	}
	
	public static void main(String[] args) {
		
	}
	
	private class LoginServe extends Thread{
		
		private Socket s;
		private User user;		// 用于保存登录用户的信息
		private Friend fr;
		ObjectInputStream ois;
		ObjectOutputStream oos;
		
		LoginServe(Socket s) {
			this.s = s;
			try {
				// 获取流对象
				oos = new ObjectOutputStream(s.getOutputStream());
				ois = new ObjectInputStream(s.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		@Override
		public void run() {
			String username, ps;
			
			try {
				// 读取客户端发来的账号密码信息
				this.user = (User)ois.readObject();
				username = user.getUserName();
				ps = user.getPassword();
				String sql = "select * from user where username=? and password=?";
				// 保证对数据库的操作不会被打断
				synchronized (con) {
					statement = con.prepareStatement(sql);
					statement.setString(1, username);
					statement.setString(2, ps);
	
					res = statement.executeQuery();
				}
				// 如果账号密码匹配
				if (res.next()) {
					// 创建用户对象，用于后续操作
					user = new User(username, ps, res.getString(3), res.getString(5));
					this.fr = new Friend(username, res.getString(3), res.getString(5));
					// 如果该用户已经在线，则直接返回
					for (LoginServe ls : vector) {
						if (ls.user.getUserName().equals(this.user.getUserName())) {
							oos.writeUTF("Had login");
							oos.flush();
							return;
						}
					}
					// 如果该用户离线，则登录	，并将他的完整信息以及当前在线的其他人写回
					oos.writeUTF("successfully login");
					oos.flush();
					oos.writeObject(user);
					oos.flush();
					oos.writeObject(curFr);
					oos.flush();
					// 将该用户上线的消息告诉其他在线的人，即通知其他用户线程更新用户列表
					Enumeration<LoginServe> enu = vector.elements();
					while(enu.hasMoreElements()) {
						LoginServe ls = enu.nextElement();
						if(ls.isAlive()) {
							ls.oos.writeUTF("one online");
							ls.oos.flush();
							ls.oos.writeObject(fr);
							ls.oos.flush();
						}
					}
					// 将本用户的信息写入当前在线列表
					curFr.addElement(fr);
					vector.addElement(this);
					// 开始与用户进行交互
					serve();
				}
				else {
					oos.writeUTF("error");
					oos.flush();
				}
			} catch (IOException e) {
				// 处理用户下线事宜，即强制退出程序会出现IOException
				synchronized(vector) {
					curFr.removeElement(fr);
					vector.removeElement(this);
				
					Enumeration<LoginServe> enu = vector.elements();
					while(enu.hasMoreElements()) {
						LoginServe ls = enu.nextElement();
						if(ls.isAlive()) {
							try {
								ls.oos.writeUTF("one offline");
								ls.oos.flush();
								ls.oos.writeObject(this.fr);
								ls.oos.flush();
							} catch (IOException e1) {
								
							}	
						}
					}
				}
			} catch (SQLException | ClassNotFoundException e) {
				e.printStackTrace();
			} finally {		// 保证资源能够得到释放
				try {
					if(this.ois != null)
						ois.close();
					if(this.oos != null)
						oos.close();
					if(this.s != null)
						this.s.close();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {			// 如果关闭时出现异常，则让对象引用变为空，让垃圾处理器回收资源
					this.ois = null;
					this.oos = null;
					this.s = null;
				}
			}		
		}
		
		// 与用户交互
		private void serve() throws IOException {		// 将异常交于上一层处理
			// 创建用于接受用户指示的字符串
			String doc = null;
			// 具体细节
			while(true) {
				// 读取命令
				doc = ois.readUTF();
				// 用户下线，该部分可能不会执行，可能会抛出异常交于上一层执行
				if(doc.equals("offline") || s.isClosed()) {
					synchronized (vector) {
						curFr.removeElement(this.fr);
						vector.removeElement(this);
						Enumeration<LoginServe> enu = vector.elements();
						// 告知其他在线的人，该用户下线，资源交于上一层处理
						while (enu.hasMoreElements()) {
							LoginServe ls = enu.nextElement();
							if (ls.isAlive()) {
								ls.oos.writeUTF("one offline");
								ls.oos.flush();
								ls.oos.writeObject(this.fr);
								ls.oos.flush();
							}
						}
					}
					break;
				}
				else if(doc.equals("singleChat")) {
					try {
						String nick = ois.readUTF();
						System.out.println(12345);
						Message message = (Message)ois.readObject();
						System.out.println(6789);
						boolean flag = false;
						synchronized (vector) {
							for (int i = 0; i < vector.size() && !flag; i++) {
								LoginServe cs = vector.get(i);
								if (cs.isAlive() && cs.user.getNick().equals(nick)) {
									flag = true;
									cs.oos.writeUTF("singleChat");
									cs.oos.flush();
									cs.oos.writeUTF(this.user.getNick());
									cs.oos.flush();
									cs.oos.writeObject(message);
									cs.oos.flush();
								}
							}
						}
						if(!flag) {
							this.oos.writeUTF("The opposite side is offline");
							this.oos.flush();
							this.oos.writeUTF(nick);
							this.oos.flush();
						}
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				}
				else if(doc.equals("reject singleChat")) {
					String nick = ois.readUTF();
					synchronized (vector) {
						for (LoginServe ls : vector) {
							if (ls != this && ls.isAlive() && ls.user.getNick().equals(nick)) {
								ls.oos.writeUTF("reject singleChat");
								ls.oos.flush();
								ls.oos.writeUTF(this.user.getNick());
								ls.oos.flush();
							}
						}
					}
				}
				else if(doc.equals("new multiChat")) {
					// 把参与多人聊天的人读到服务器
					Vector<String> people;
					try {
						people = (Vector<String>)ois.readObject();
					} catch (ClassNotFoundException e) {
						System.out.println("多人聊天出现异常");
						continue;
					}
					MultiChatInfo info = new MultiChatInfo(multichat_id++, people);
					multiChatInfos.addElement(info);
					// 告知群聊发起者可以创建群聊窗口，并将群聊的编号（ID）以及群聊成员发回
					oos.writeUTF("set up multiChat");
					oos.flush();
					oos.writeObject(info);
					oos.flush();
					// 通知其他人有人发起群聊
					for(String s : people) {
						if(s.equals(this.user.getNick())) continue;
						for(LoginServe ls : vector) {
							if(ls.isAlive() && s.equals(ls.user.getNick())) {
								ls.oos.writeUTF("new multiChat");
								ls.oos.flush();
								ls.oos.writeUTF(this.user.getNick());
								ls.oos.flush();
								ls.oos.writeObject(info);
								ls.oos.flush();
							}
						}
					}
				}
				else if(doc.equals("reject multiChat")) {
					int id = ois.readInt();
					Vector<String> people = multiChatInfos.get(id).getPeople();
					people.removeElement(this.user.getNick());
					for(String nick : people) {
						for(LoginServe ls : vector) {
							if(ls.user.getNick().equals(nick) && ls.isAlive()) {
								ls.oos.writeUTF("reject multiChat");
								ls.oos.flush();
								ls.oos.writeUTF(this.user.getNick());
								ls.oos.flush();
								ls.oos.writeInt(id);
								ls.oos.flush();
								break;
							}
						}
					}
				}
				else if(doc.equals("multiChat")) {
					int id = ois.readInt();
					Message message = null;
					try {
						message = (Message)ois.readObject();
					} catch (ClassNotFoundException e) {
						continue;
					}
					Vector<String> people = multiChatInfos.get(id).getPeople();
					for(String s : people) {
						if(s.equals(this.user.getNick())) continue;
						for(LoginServe ls : vector) {
							if(ls.isAlive() && ls.user.getNick().equals(s)) {
								ls.oos.writeUTF("multiChat");
								ls.oos.flush();
								ls.oos.writeInt(id);
								ls.oos.flush();
								ls.oos.writeUTF(this.user.getNick());
								ls.oos.flush();
								ls.oos.writeObject(message);
								ls.oos.flush();
								break;
							}
						}
					}
				}
			}
		}
	}
}
