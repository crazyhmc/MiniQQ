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
	// ��ǰ�����û�
	private Vector<Friend> curFr;
	private Vector<LoginServe> vector;

	// �����������
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
				// �����µ��߳�
				LoginServe temp = new LoginServe(socket);
				temp.start();
			}
		}
	}
	
	public static void main(String[] args) {
		
	}
	
	private class LoginServe extends Thread{
		
		private Socket s;
		private User user;		// ���ڱ����¼�û�����Ϣ
		private Friend fr;
		ObjectInputStream ois;
		ObjectOutputStream oos;
		
		LoginServe(Socket s) {
			this.s = s;
			try {
				// ��ȡ������
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
				// ��ȡ�ͻ��˷������˺�������Ϣ
				this.user = (User)ois.readObject();
				username = user.getUserName();
				ps = user.getPassword();
				String sql = "select * from user where username=? and password=?";
				// ��֤�����ݿ�Ĳ������ᱻ���
				synchronized (con) {
					statement = con.prepareStatement(sql);
					statement.setString(1, username);
					statement.setString(2, ps);
	
					res = statement.executeQuery();
				}
				// ����˺�����ƥ��
				if (res.next()) {
					// �����û��������ں�������
					user = new User(username, ps, res.getString(3), res.getString(5));
					this.fr = new Friend(username, res.getString(3), res.getString(5));
					// ������û��Ѿ����ߣ���ֱ�ӷ���
					for (LoginServe ls : vector) {
						if (ls.user.getUserName().equals(this.user.getUserName())) {
							oos.writeUTF("Had login");
							oos.flush();
							return;
						}
					}
					// ������û����ߣ����¼	����������������Ϣ�Լ���ǰ���ߵ�������д��
					oos.writeUTF("successfully login");
					oos.flush();
					oos.writeObject(user);
					oos.flush();
					oos.writeObject(curFr);
					oos.flush();
					// �����û����ߵ���Ϣ�����������ߵ��ˣ���֪ͨ�����û��̸߳����û��б�
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
					// �����û�����Ϣд�뵱ǰ�����б�
					curFr.addElement(fr);
					vector.addElement(this);
					// ��ʼ���û����н���
					serve();
				}
				else {
					oos.writeUTF("error");
					oos.flush();
				}
			} catch (IOException e) {
				// �����û��������ˣ���ǿ���˳���������IOException
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
			} finally {		// ��֤��Դ�ܹ��õ��ͷ�
				try {
					if(this.ois != null)
						ois.close();
					if(this.oos != null)
						oos.close();
					if(this.s != null)
						this.s.close();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {			// ����ر�ʱ�����쳣�����ö������ñ�Ϊ�գ�������������������Դ
					this.ois = null;
					this.oos = null;
					this.s = null;
				}
			}		
		}
		
		// ���û�����
		private void serve() throws IOException {		// ���쳣������һ�㴦��
			// �������ڽ����û�ָʾ���ַ���
			String doc = null;
			// ����ϸ��
			while(true) {
				// ��ȡ����
				doc = ois.readUTF();
				// �û����ߣ��ò��ֿ��ܲ���ִ�У����ܻ��׳��쳣������һ��ִ��
				if(doc.equals("offline") || s.isClosed()) {
					synchronized (vector) {
						curFr.removeElement(this.fr);
						vector.removeElement(this);
						Enumeration<LoginServe> enu = vector.elements();
						// ��֪�������ߵ��ˣ����û����ߣ���Դ������һ�㴦��
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
					// �Ѳ������������˶���������
					Vector<String> people;
					try {
						people = (Vector<String>)ois.readObject();
					} catch (ClassNotFoundException e) {
						System.out.println("������������쳣");
						continue;
					}
					MultiChatInfo info = new MultiChatInfo(multichat_id++, people);
					multiChatInfos.addElement(info);
					// ��֪Ⱥ�ķ����߿��Դ���Ⱥ�Ĵ��ڣ�����Ⱥ�ĵı�ţ�ID���Լ�Ⱥ�ĳ�Ա����
					oos.writeUTF("set up multiChat");
					oos.flush();
					oos.writeObject(info);
					oos.flush();
					// ֪ͨ���������˷���Ⱥ��
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
