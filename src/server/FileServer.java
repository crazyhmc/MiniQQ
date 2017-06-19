package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

import objects.User;

public class FileServer {
	// 交流信息用的端口
	private static final int PORT = 9000;
	// 服务器接收文件用的端口
	private static final int FILE_ACCEPT_PORT = 9001;
	// 收件方请求文件所用端口
	private static final int FILE_SEND_PORT = 9002;
	private ServerSocket server;
	private ServerSocket accept_server;
	private ServerSocket send_server;
	private Vector<fileServe> fileServes;
	private Vector<OfflineFile> offline_files;

	FileServer() {
		try {
			server = new ServerSocket(PORT);
			accept_server = new ServerSocket(FILE_ACCEPT_PORT);
			send_server = new ServerSocket(FILE_SEND_PORT);
			fileServes = new Vector<>();
			offline_files = new Vector<>();
		} catch (IOException e) {

		}
		new Thread(() -> {
            while(true) {
                try {
                    Socket s = accept_server.accept();
                    new Thread(() -> file_accept(s)).start();
                } catch (IOException e) {
                    return;
                }
            }
        }).start();

		new Thread(() -> {
			while(true) {
				try {
					Socket s = send_server.accept();
					new Thread(() -> file_send(s)).start();
				} catch (IOException e) {
					return;
				}
			}
        }).start();
	}

	// 文件接收
	private void file_accept(Socket s) {
		try {
			// 获取输入流
			ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
			// 获取文件名
			String filename = ois.readUTF();
			// 获取收件人昵称
			String nick = ois.readUTF();
			// 开启流向文件写
			FileOutputStream fout = new FileOutputStream(new File("./File_temp/" + nick + "_" + filename));
			int b;
			byte[] tmp = new byte[32768];
			while((b = ois.read(tmp, 0, tmp.length)) != -1) {
				fout.write(tmp, 0, b);
			}
			fout.close();
			ois.close();
			s.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// 客户端请求文件
	private void file_send(Socket s) {
		try {
			// 获取输入输出流
			ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
			ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
			// 获取要请求的文件路径
			File file = (File)ois.readObject();
			// 从文件读取
			FileInputStream fin = new FileInputStream(file);
			int b;
			byte[] tmp = new byte[32768];
			while((b = fin.read(tmp, 0, tmp.length)) != -1) {
				oos.write(tmp, 0, b);
				oos.flush();
			}
			fin.close();
			ois.close();
			oos.close();
			s.close();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	// 等待客户连接
	void chatAccept() {
		while(true) {
			try {
				Socket socket = server.accept();
				fileServe fileserve = new fileServe(socket);
				// 将与客户端进行交互的线程存入Vector中
				fileServes.addElement(fileserve);
				fileserve.start();
			} catch (IOException e) {
				return;
			}
		}
	}

	// 与客户端交互的线程类
	private class fileServe extends Thread {
		private Socket socket;
		private ObjectInputStream ois;
		private ObjectOutputStream oos;
		private User user;

		fileServe(Socket socket) {
			this.socket = socket;
			try {
				oos = new ObjectOutputStream(this.socket.getOutputStream());
				ois = new ObjectInputStream(this.socket.getInputStream());

				user = (User)ois.readObject();
				/* 检查是否存在离线文件 */
				new Thread(() -> {
                    for (OfflineFile of : offline_files) {
                        if(of.getTo().equals(user.getNick())) {
                            try {
                                oos.writeUTF("offline_fileTrans");
                                oos.flush();
                                oos.writeUTF(of.getFrom());
                                oos.flush();
                                oos.writeObject(of.getFile());
                                oos.flush();
                                oos.writeUTF(of.getFilename());
                                oos.flush();
                            } catch (IOException e) {
                                return;
                            }
                        }
                    }
                }).start();
			} catch (IOException | ClassNotFoundException e) {

			}
		}

		@Override
		public void run() {
			file_chat();
		}

		// 与客户端进行交互
		private void file_chat() {
			// 客户端发来的指令
			String cmd;
			// 文件的发送者或收件人
			String nick;
			try {
				while(true) {
					cmd = ois.readUTF();
					// 下线则结束
					if(cmd.equals("offline")) break;
					nick = ois.readUTF();
					switch (cmd) {
						case "offline_fileTrans" : {
							// 关键代码与在线相同，所以在在线传输中做区分，避免重复代码
						}
						// 在线文件传输
						case "online_fileTrans" : {
							String filename = ois.readUTF();
							File file = new File("./File_temp/" + this.user.getNick() + "_" + filename);
							// 告知文件已完全上传至服务器
							String t = ois.readUTF();
							boolean flag = false;
							// 告诉对方有文件，如果对方在线，则与在线传输没差别，故按在线处理
							for (fileServe fs : fileServes) {
								if(fs != this && fs.isAlive() && fs.user.getNick().equals(nick)) {
									System.out.println(fs.user.getNick());
									fs.oos.writeUTF("online_fileTrans");
									fs.oos.flush();
									fs.oos.writeUTF(this.user.getNick());
									fs.oos.flush();
									fs.oos.writeObject(file);
									fs.oos.flush();
									fs.oos.writeUTF(filename);
									fs.oos.flush();
									flag = true;
									break;
								}
							}
							// 对方如果不在线，且是在线传输
							if(!flag && cmd.equals("online_fileTrans")) {
								this.oos.writeUTF("opposite side is offline");
								this.oos.flush();
								this.oos.writeUTF(nick);
								this.oos.flush();
								file.delete();
							}
							else if(!flag && cmd.equals("offline_fileTrans")) {
								this.oos.writeUTF("upload successfully");
								this.oos.flush();
								this.oos.writeUTF(nick);
								this.oos.flush();
								offline_files.addElement(new OfflineFile(this.user.getNick(), nick,filename, file));
							}
							break;
						}
						// 接收在线文件，接收过程由另一端口负责
						case "accept" : {
							File file = (File)ois.readObject();
							// 告知服务器文件下载完成
							String t = ois.readUTF();
							for (OfflineFile of : offline_files) {
								if(of.getFile().getName().equals(file.getName())) {
									offline_files.removeElement(of);
									break;
								}
							}
							file.delete();
							for (fileServe fs : fileServes) {
								if(fs != this && fs.isAlive() && fs.user.getNick().equals(nick)) {
									fs.oos.writeUTF("has_accepted");
									fs.oos.flush();
									fs.oos.writeUTF(this.user.getNick());
									fs.oos.flush();
								}
							}
							break;
						}
						// 拒绝在线文件
						case "reject" : {
							File file = (File)ois.readObject();
							for (OfflineFile of : offline_files) {
								if(of.getFile().getName().equals(file.getName())) {
									offline_files.removeElement(of);
									break;
								}
							}
							file.delete();
							// 告知发件人被拒绝，如发件人离线，则不予与处理
							for (fileServe fs : fileServes) {
								if(fs.isAlive() && fs.user.getNick().equals(nick)) {
									fs.oos.writeUTF("has_rejected");
									fs.oos.flush();
									fs.oos.writeUTF(this.user.getNick());
									fs.oos.flush();
								}
							}
							break;
						}
					}
				}
			} catch (IOException e) {

			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				// 线程结束前关闭流，并从Vector中移除
				try {
					ois.close();
					oos.close();
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					ois = null;
					oos = null;
					socket = null;
					fileServes.removeElement(this);
				}
			}
		}
	}

	// 离线文件类
	private class OfflineFile {
		private String from;
		private String to;
		private String filename;
		private File file;

		OfflineFile(String from, String to, String filename, File file) {
			this.from = from;
			this.to = to;
			this.filename = filename;
			this.file = file;
		}

		String getFrom() {
			return from;
		}

		String getTo() {
			return to;
		}

		File getFile() {
			return file;
		}

		String getFilename() {
			return filename;
		}
	}
}
