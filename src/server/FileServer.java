package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

import objects.User;

public class FileServer {
	// ������Ϣ�õĶ˿�
	private static final int PORT = 9000;
	// �����������ļ��õĶ˿�
	private static final int FILE_ACCEPT_PORT = 9001;
	// �ռ��������ļ����ö˿�
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

	// �ļ�����
	private void file_accept(Socket s) {
		try {
			// ��ȡ������
			ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
			// ��ȡ�ļ���
			String filename = ois.readUTF();
			// ��ȡ�ռ����ǳ�
			String nick = ois.readUTF();
			// ���������ļ�д
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

	// �ͻ��������ļ�
	private void file_send(Socket s) {
		try {
			// ��ȡ���������
			ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
			ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
			// ��ȡҪ������ļ�·��
			File file = (File)ois.readObject();
			// ���ļ���ȡ
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

	// �ȴ��ͻ�����
	void chatAccept() {
		while(true) {
			try {
				Socket socket = server.accept();
				fileServe fileserve = new fileServe(socket);
				// ����ͻ��˽��н������̴߳���Vector��
				fileServes.addElement(fileserve);
				fileserve.start();
			} catch (IOException e) {
				return;
			}
		}
	}

	// ��ͻ��˽������߳���
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
				/* ����Ƿ���������ļ� */
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

		// ��ͻ��˽��н���
		private void file_chat() {
			// �ͻ��˷�����ָ��
			String cmd;
			// �ļ��ķ����߻��ռ���
			String nick;
			try {
				while(true) {
					cmd = ois.readUTF();
					// ���������
					if(cmd.equals("offline")) break;
					nick = ois.readUTF();
					switch (cmd) {
						case "offline_fileTrans" : {
							// �ؼ�������������ͬ�����������ߴ����������֣������ظ�����
						}
						// �����ļ�����
						case "online_fileTrans" : {
							String filename = ois.readUTF();
							File file = new File("./File_temp/" + this.user.getNick() + "_" + filename);
							// ��֪�ļ�����ȫ�ϴ���������
							String t = ois.readUTF();
							boolean flag = false;
							// ���߶Է����ļ�������Է����ߣ��������ߴ���û��𣬹ʰ����ߴ���
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
							// �Է���������ߣ��������ߴ���
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
						// ���������ļ������չ�������һ�˿ڸ���
						case "accept" : {
							File file = (File)ois.readObject();
							// ��֪�������ļ��������
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
						// �ܾ������ļ�
						case "reject" : {
							File file = (File)ois.readObject();
							for (OfflineFile of : offline_files) {
								if(of.getFile().getName().equals(file.getName())) {
									offline_files.removeElement(of);
									break;
								}
							}
							file.delete();
							// ��֪�����˱��ܾ����緢�������ߣ������봦��
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
				// �߳̽���ǰ�ر���������Vector���Ƴ�
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

	// �����ļ���
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
