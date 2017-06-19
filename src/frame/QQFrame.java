package frame;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.Date;
import java.util.Vector;

import javax.swing.*;
import javax.swing.border.*;

import objects.Friend;
import objects.Message;
import objects.MultiChatInfo;
import objects.User;

public class QQFrame extends JFrame {
	private Vector<String> arr = new Vector<>();
	private Vector<Friend> curFr;
	private JButton ql = new JButton("新建群聊");
	private User self;
	private JList<String> list;
	private Vector<ChatFrame> cf;
	private Vector<MultiChatFrame> mcf;
	private static final String SERVER_IP = "127.0.0.1";
	
	// 登录及聊天
	private Socket socket;
	private ObjectInputStream ois;
	private ObjectOutputStream oos;

	// 文件传输部分
	private Socket fileSocket;
	private ObjectInputStream filein;
	private ObjectOutputStream fileout;

	QQFrame(Socket ss, ObjectInputStream in, ObjectOutputStream out, User self, Vector<Friend> curFr) {
		// QQFrame对象初始化
		this.socket = ss;
		this.ois = in;
		this.oos = out;
		this.self = self;
		this.curFr = curFr;
		this.arr.removeAllElements();
		for (Friend aCurFr : curFr) {
			this.arr.addElement(aCurFr.getNick());
		}
		this.cf = new Vector<>();
		this.mcf = new Vector<>();

		// 文件部分初始化
		try {
			this.fileSocket = new Socket(SERVER_IP, 9000);
			this.filein = new ObjectInputStream(this.fileSocket.getInputStream());
			this.fileout = new ObjectOutputStream(this.fileSocket.getOutputStream());
			this.fileout.writeObject(this.self);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// 设置标题，布局管理器，以及全局字体
		setTitle("MyQQ");
		((JPanel)this.getContentPane()).setOpaque(false);
		ImageIcon img = new ImageIcon("./images/qq6.jpg");
		JLabel background=new JLabel(img);
		this.getLayeredPane().add(background,new Integer(Integer.MIN_VALUE));
		background.setBounds(0, 0, img.getIconWidth(), img.getIconHeight());
		setLayout(new BorderLayout());
		Font font = new Font(Font.SERIF, Font.PLAIN, 20);
		
		// 创建在线列表
		list = new JList<>();
		list.setCellRenderer(new listItem());
		list.setListData(arr);
		list.setOpaque(false);
		JScrollPane jsp = new JScrollPane();
		jsp.getViewport().setOpaque(false);

		jsp.getViewport().add(list);
		add(jsp, BorderLayout.CENTER);
		
		// 创建个人信息面板
		JPanel pane = new JPanel();
		pane.setOpaque(false);
		pane.setLayout(new GridLayout(2, 2));
		JLabel nick = new JLabel("昵称");
		pane.add(nick);
		nick.setFont(font);
		JLabel nickValue = new JLabel(this.self.getNick());
		nickValue.setFont(font);
		pane.add(nickValue);

		JLabel sex = new JLabel("性别");
		sex.setFont(font);
		pane.add(sex);
		JLabel sexValue = new JLabel(this.self.getSex().equals("male") ? "男" : "女");
		sexValue.setFont(font);
		pane.add(sexValue);
		
		add(pane, BorderLayout.NORTH);
		setSize(350, 800);

		ql.setFont(new Font(Font.SERIF, Font.PLAIN, 15));
		add(ql, BorderLayout.SOUTH);
		setResizable(false);
		// 获取屏幕大小
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		// 将窗体居中
		setLocation(screen.width/2-175, screen.height/2-400);
		setVisible(true);
		
		// 添加事件
		event();
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		
		// 设置观感
		String plaf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
		try {
			UIManager.setLookAndFeel(plaf);
			SwingUtilities.updateComponentTreeUI(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 创建新的线程与服务器进行交互
		new Thread(QQFrame.this::exchange).start();
		new Thread(QQFrame.this::chatWithFileServer).start();
	}

	private class listItem extends JLabel implements ListCellRenderer<String> {
		private Border
				selectedBorder = BorderFactory.createLineBorder(Color.blue,1),
				emptyBorder = BorderFactory.createEmptyBorder(1,1,1,1);
		private Font font = new Font(Font.SERIF, Font.PLAIN, 20);
		public Component getListCellRendererComponent(JList<? extends String> list,
													  String val, int index, boolean isSelected, boolean cellHasFocus) {
			this.setFont(font);
			this.setText(val);
			if ( isSelected ) {
				setBorder (selectedBorder);
			}
			else setBorder(emptyBorder);
			this.setOpaque(false);
			return this;
		}
	}

	private void event() {
		// 
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				try {
					// 退出前告知服务器
					oos.writeUTF("offline");
					oos.flush();
					fileout.writeUTF("offline");
					fileout.flush();
					fileout.close();
					filein.close();
					fileSocket.close();
					if(oos != null) 
						oos.close();
					if(ois != null) 
						ois.close();
					if(socket != null)
						socket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				} finally {
					dispose();
					filein = null;
					fileout = null;
					fileSocket = null;
					ois = null;
					oos = null;
					socket = null;
				}
			}
		});
		
		// 为列表添加事件监听
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if(e.getClickCount() == 2) {
					String nickname = list.getSelectedValue();
					// 检查与对方的聊天窗口是否存在
					for (ChatFrame aCf : cf) {
						if (aCf.title.equals(nickname)) {
							aCf.toFront();
							return;
						}
					}
					ChatFrame temp = new ChatFrame(nickname);
					QQFrame.this.cf.addElement(temp);
				}
			}
			
		});
		
		// 为群聊按钮添加事件
		ql.addActionListener(e -> {
			if(arr.size() == 0) {
				JOptionPane.showMessageDialog(QQFrame.this, "当前在线人数为0，无法开启群聊", "Sorry", JOptionPane.PLAIN_MESSAGE);
				return;
			}
			new QLSelector(arr);
		});

	}
	
	// 与服务器交互
	private void exchange() {
		// 创建接收服务器回应的字符串
		String doc;
		
		while(true) {
			try {
				// 如果下线则被强制打断，将出现IOException
				doc = ois.readUTF();
				// 当有人下线时，更新在线列表
				switch (doc) {
					case "one offline":
						Friend fr = (Friend) ois.readObject();
						curFr.removeElement(fr);
						arr.removeElement(fr.getNick());
						list.setListData(arr);
						break;
					// 当有人上线，更新在线列表
					case "one online":
						Friend newFr = (Friend) ois.readObject();
						curFr.addElement(newFr);
						arr.addElement(newFr.getNick());
						list.setListData(arr);
						break;
					// 当发送消息时，对方恰好离线，接收消息失败，则弹窗告知用户
					case "The opposite side is offline": {
						String nick = ois.readUTF();
						JFrame parent = this;
						for (ChatFrame aCf : this.cf) {
							if (aCf.title.equals(nick)) {
								parent = aCf;
								break;
							}
						}
						JOptionPane.showMessageDialog(parent, nick + "已离线，消息发送失败", "提示", JOptionPane.WARNING_MESSAGE);
						break;
					}
					// 正常单人聊天，接收对方发来消息
					case "singleChat": {
						String nick = ois.readUTF();
						Message message = (Message) ois.readObject();
						boolean flag = false;    // 树立标记，用来表示当前与对方的聊天窗口是否存在

						// 检查与对方的聊天窗口是否存在，存在则更新标记，并更新消息栏
						for (int i = 0; i < this.cf.size() && !flag; i++) {
							ChatFrame temp = this.cf.get(i);
							if (temp.title.equals(nick)) {
								temp.text1.append(message.getDate() + " " + nick + "说\n" + message.getMessage() + "\r\n\r\n");
								// 将光标设置为行末，以实现滚动面板自动往下移动
								temp.text1.setCaretPosition(temp.text1.getText().length());
								flag = true;
							}
						}
						// 如果不存在则新建窗口
						if (!flag) {
							String[] options = {"接受", "丑拒"};
							int res = JOptionPane.showOptionDialog(this, nick + "向您发起聊天", "提示", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
							if (res == 0) {
								ChatFrame temp = new ChatFrame(nick);
								temp.text1.append(message.getDate() + " " + nick + "说\n" + message.getMessage() + "\r\n\r\n");
								// 将光标设置为行末，以实现滚动面板自动往下移动
								temp.text1.setCaretPosition(temp.text1.getText().length());
								this.cf.addElement(temp);
							} else {
								oos.writeUTF("reject singleChat");
								oos.flush();
								oos.writeUTF(nick);
								oos.flush();
							}
						}
						break;
					}
					case "reject singleChat": {
						String nick = ois.readUTF();
						JFrame temp = this;
						for (ChatFrame aCf : cf) {
							if (aCf.title.equals(nick)) {
								temp = aCf;
								break;
							}
						}
						JOptionPane.showMessageDialog(temp, nick + "拒绝和你说话!", "Sorry", JOptionPane.PLAIN_MESSAGE);
						break;
					}
					case "set up multiChat" : {
						MultiChatInfo info = (MultiChatInfo)ois.readObject();
						System.out.println(info.getId() + "  " + info.getPeople());
						MultiChatFrame temp = new MultiChatFrame(info);
						mcf.addElement(temp);
						break;
					}
					case "new multiChat" : {
						String nick = ois.readUTF();
						MultiChatInfo info = (MultiChatInfo)ois.readObject();
						String[] options = {"接受", "丑拒" };
						int val = JOptionPane.showOptionDialog(this, nick + "发起了多人聊天", "提示", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
						if(val == 0) {
							MultiChatFrame temp = new MultiChatFrame(info);
							mcf.addElement(temp);
						}
						else {
							oos.writeUTF("reject multiChat");
							oos.flush();
							oos.writeInt(info.getId());
							oos.flush();
						}
						break;
					}
					case "reject multiChat" : {
						String nick = ois.readUTF();
						int id = ois.readInt();
						// 给多人聊天窗口写消息
						for(MultiChatFrame cur : this.mcf) {
							if(cur.info.getId() == id) {
								cur.text1.append(nick + "离开了该多人聊天\n\n");
								cur.info.getPeople().removeElement(nick);
								cur.list.setListData(cur.info.getPeople());
								cur.text1.setCaretPosition(cur.text1.getText().length());
								cur.list.setListData(cur.info.getPeople());
								break;
							}
						}

						break;
					}
					case "multiChat" : {
						int id = ois.readInt();
						String nick = ois.readUTF();
						Message message = (Message)ois.readObject();
						for(MultiChatFrame cur : mcf) {
							if(cur.info.getId() == id) {
								cur.text1.append(message.getDate() + " " + nick + "说\n" + message.getMessage() + "\r\n\r\n");
								// 将光标设置为行末，以实现滚动面板自动往下移动
								cur.text1.setCaretPosition(cur.text1.getText().length());
								break;
							}
						}
						break;
					}
				}
			} catch (IOException e) {
				// 离线前先清除在线列表
				arr.removeAllElements();
				list.setListData(arr);
				return;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			
		}
	}

	// 与文件服务端进行交互
	private void chatWithFileServer() {
		// 获取指令
		String cmd;
		// 获取昵称
		String nick;
		// 可能出现的弹窗的父窗口
		JFrame parent;
		try {
			while(true) {
				cmd = filein.readUTF();
				nick = filein.readUTF();
				// 如果存在与对方的聊天窗口，则父窗口为该聊天窗口，否则为QQ窗口
				parent = this;
				for (ChatFrame cur : this.cf) {
					if(cur.title.equals(nick)) {
						parent = cur;
						break;
					}
				}
				switch (cmd) {
					case "offline_fileTrans" :
					// 在线文件，说明有人发来文件
					case "online_fileTrans" : {
						// 获取文件在服务器端的路径，方便请求文件
						File file = (File)filein.readObject();
						// 获取文件名，以方便用户知道文件的名称以及格式
						String filename = filein.readUTF();
						String[] options = {"接收", "丑拒"};
						int val = JOptionPane.showOptionDialog(parent, nick + "向您发送文件 " + filename, "提示", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
						if (val == 0) {
							JFileChooser chooser = new JFileChooser();
							chooser.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
							int res = chooser.showSaveDialog(parent);
							if(res == JFileChooser.APPROVE_OPTION) {
								// 通知服务器告知对方已同意接收对方文件，并让服务器等待接收完成的指令
								fileout.writeUTF("accept");
								fileout.flush();
								fileout.writeUTF(nick);
								fileout.flush();
								fileout.writeObject(file);
								fileout.flush();

								// 请求服务器文件
								Socket s = new Socket( SERVER_IP,9002);
								ObjectInputStream objin = new ObjectInputStream(s.getInputStream());
								ObjectOutputStream objout = new ObjectOutputStream(s.getOutputStream());
								String path = chooser.getCurrentDirectory().toString()
										+ File.separator + chooser.getSelectedFile().getName();
								File localFile = new File(path);
								objout.writeObject(file);
								objout.flush();
								try (FileOutputStream fout = new FileOutputStream(localFile)) {
									int b;
									byte[] temp = new byte[32768];
									while((b = objin.read(temp, 0, temp.length)) != -1) {
										fout.write(temp, 0, b);
									}
								}
								objin.close();
								objout.close();
								s.close();
								// 告知服务器完成接收
								fileout.writeUTF("ok");
								fileout.flush();
							}
							else {
								// 拒收
								fileout.writeUTF("reject");
								fileout.flush();
								fileout.writeUTF(nick);
								fileout.flush();
								fileout.writeObject(file);
								fileout.flush();
							}
						}
						else {
							// 拒收
							fileout.writeUTF("reject");
							fileout.flush();
							fileout.writeUTF(nick);
							fileout.flush();
							fileout.writeObject(file);
							fileout.flush();
						}
						break;
					}
					// 得到对方已经成功接收的指令
					case "has_accepted" : {
						JOptionPane.showMessageDialog(parent, nick + "已成功接收你的文件", "恭喜", JOptionPane.PLAIN_MESSAGE);
						break;
					}
					// 得到对方拒收的指令
					case "has_rejected" : {
						JOptionPane.showMessageDialog(parent, nick + "拒绝接收你的文件", "Sorry", JOptionPane.PLAIN_MESSAGE);
						break;
					}
					// 得到对方下线的指令
					case "opposite side is offline" : {
						JOptionPane.showMessageDialog(parent, nick + "已离线，文件传输失败", "Sorry", JOptionPane.PLAIN_MESSAGE);
						break;
					}
					case "upload successfully" : {
						JOptionPane.showMessageDialog(parent, "离线文件已上传至服务器，等待对方上线接收", "提示", JOptionPane.PLAIN_MESSAGE);
						break;
					}
				}
			}
		} catch (IOException e) {
			return;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	// 聊天窗口
	private class ChatFrame extends JFrame {
		private JScrollPane jp_up;
		private JScrollPane jp_down;
		private JTextArea text1;
		private JTextArea text2;
		private JPanel p1;
		private JPanel p2;
		private JButton send;
		private JButton clear;
		private JButton file_send;
		private JButton exit;
		private String title;
		private JComboBox<String> jcb;

		ChatFrame(String title) {
			super();
			this.title = title;
			setTitle(this.title);
			((JPanel)this.getContentPane()).setOpaque(false);
			ImageIcon img = new ImageIcon("./images/qq14.jpg");
			JLabel background=new JLabel(img);
			this.getLayeredPane().add(background,new Integer(Integer.MIN_VALUE));
			background.setBounds(0, 0, img.getIconWidth(), img.getIconHeight());
			setLayout(new BorderLayout());
			Font font = new Font("Helvetica Bold", Font.BOLD, 16);

			// 消息显示框
			text1 = new JTextArea(15, 50);
			text1.setLineWrap(true);    // 设置自动换行
			text1.setEditable(false);   // 禁止用户编辑
			text1.setFont(font);
			text1.setOpaque(false);
			jp_up = new JScrollPane(text1);    // 给文本域增加滚动条
			jp_up.getViewport().setOpaque(false);
			jp_up.setOpaque(false);
			p1 = new JPanel();
			p1.setOpaque(false);
			p1.add(jp_up);
			add(p1, BorderLayout.CENTER);

			JPanel temp = new JPanel();
			temp.setOpaque(false);
			temp.setLayout(new BorderLayout());
			JPanel tup = new JPanel();
			tup.setOpaque(false);
			tup.setLayout(new FlowLayout());
			file_send = new JButton("文件传输");
			file_send.setFont(font);
			clear = new JButton("清屏");
			clear.setFont(font);
			jcb = new JComboBox<>();
			jcb.addItem("黑色");
			jcb.addItem("蓝色");
			jcb.addItem("红色");
			jcb.setFont(font);
			tup.add(jcb);
			tup.add(file_send);
			tup.add(clear);
			temp.add(tup, BorderLayout.NORTH);

			// 消息输入框
			text2 = new JTextArea(8, 50);
			text2.setLineWrap(true);    // 设置自动换行
			text2.setFont(font);
			text2.setOpaque(false);
			jp_down = new JScrollPane(text2); // 给文本域增加滚动条
			jp_down.setOpaque(false);
			jp_down.getViewport().setOpaque(false);
			p2 = new JPanel();
			p2.setOpaque(false);
			p2.add(jp_down);
			temp.add(p2, BorderLayout.CENTER);

			// 发送，清屏按钮
			send = new JButton("发送");
			send.setFont(font);
			exit = new JButton("退出");
			exit.setFont(font);
			tup = new JPanel();
			tup.setOpaque(false);
			tup.setLayout(new FlowLayout());
			tup.add(send);
			tup.add(exit);
			temp.add(tup, BorderLayout.SOUTH);
			add(temp, BorderLayout.SOUTH);
			pack();
			// 获取屏幕大小
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			// 将窗体居中
			setLocation(screen.width / 2 - this.getWidth() / 2, screen.height / 2 - this.getHeight() / 2);
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);


			// 设置观感
			String plaf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
			try {
				UIManager.setLookAndFeel(plaf);
				SwingUtilities.updateComponentTreeUI(this);
			} catch (Exception e) {
				e.printStackTrace();
			}

			event();
			setResizable(false);
			setVisible(true);
		}

		// 为组件添加事件监听
		private void event() {
			send.addActionListener(event -> ChatFrame.this.sendMessage());

			// 清屏
			this.clear.addActionListener(e -> {
				ChatFrame.this.text1.setText("");
				ChatFrame.this.text2.setText("");
			});

			// 键盘事件
			KeyListener key = new KeyAdapter() {

				@Override
				public void keyPressed(KeyEvent e) {
					// ESC 关闭聊天窗口
					if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
						ChatFrame.this.dispose();
						QQFrame.this.cf.removeElement(ChatFrame.this);
					}
					// Ctrl + Enter 为发送消息
					else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
						ChatFrame.this.sendMessage();
					}
				}
			};
			// 为所有组件添加键盘事件
			text1.addKeyListener(key);
			text2.addKeyListener(key);
			send.addKeyListener(key);
			clear.addKeyListener(key);
			file_send.addKeyListener(key);
			jcb.addKeyListener(key);
			exit.addKeyListener(key);
			this.addKeyListener(key);

			exit.addActionListener(e -> {
				ChatFrame.this.dispose();
				QQFrame.this.cf.removeElement(ChatFrame.this);
			});
			jcb.addActionListener((ActionEvent e) -> {
				String s = jcb.getItemAt(jcb.getSelectedIndex());
				Color color;
				if(s.equals("黑色")) color = Color.BLACK;
				else if(s.equals("蓝色")) color = Color.BLUE;
				else color = Color.RED;
				text1.setForeground(color);
				text2.setForeground(color);
			});

			// 文件传输事件
			this.file_send.addActionListener(event -> {
				String[] options = {"在线传输", "离线传输"};
				int val = JOptionPane.showOptionDialog(ChatFrame.this, "请选择传输方式", "您好", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
				if(val == 0)
					new Thread(() -> ChatFrame.this.sendFile("online_fileTrans")).start();
				else if(val == 1)
					new Thread(() -> ChatFrame.this.sendFile("offline_fileTrans")).start();
			});
			// 关闭时将它从向量中移除
			this.addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosing(WindowEvent e) {
					QQFrame.this.cf.removeElement(ChatFrame.this);
				}
			});
		}

		// 发送消息
		private void sendMessage() {
			Date cur = new Date();
			Message message = new Message(text2.getText(), cur);

			if (message.getMessage().equals("")) {
				JOptionPane.showMessageDialog(this, "消息不能为空", "提示", JOptionPane.WARNING_MESSAGE);
				return;
			}

			try {
				oos.writeUTF("singleChat");
				oos.flush();
				oos.writeUTF(this.title);
				oos.flush();
				oos.writeObject(message);
				oos.flush();
				text1.append(message.getDate() + " 我说:\n" + message.getMessage() + "\r\n\r\n");
				// 将光标设置为行末，以实现滚动面板自动往下移动
				text1.setCaretPosition(text1.getText().length());
				text2.setText("");
			} catch (IOException ignored) {
			}
		}

		// 发送文件
		private void sendFile(String status) {
			JFileChooser chooser = new JFileChooser();
			chooser.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
			int returnVal = chooser.showOpenDialog(this);
			if(returnVal == JFileChooser.APPROVE_OPTION) {
				String path = chooser.getCurrentDirectory().toString()
						+ File.separator + chooser.getSelectedFile().getName();
				File file = new File(path);
				try {
					FileInputStream fin = new FileInputStream(file);
					fileout.writeUTF(status);
					fileout.flush();
					fileout.writeUTF(this.title);
					fileout.flush();
					fileout.writeUTF(file.getName());
					fileout.flush();

					Socket s = new Socket(SERVER_IP ,9001);
					ObjectOutputStream objout = new ObjectOutputStream(s.getOutputStream());
					objout.writeUTF(file.getName());
					objout.flush();
					objout.writeUTF(QQFrame.this.self.getNick());
					objout.flush();

					int b;
					byte[] temp = new byte[32768];
					while((b = fin.read(temp, 0, temp.length)) != -1) {
						objout.write(temp, 0, b);
					}
					fin.close();
					objout.close();
					s.close();
					fileout.writeUTF("ok");
					fileout.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// 多人聊天窗口
	private class MultiChatFrame extends JFrame{
		private JList<String> list;
		private JScrollPane jp_up;
		private JScrollPane jp_down;
		private JTextArea text1;
		private JTextArea text2;
		private JPanel p1;
		private JPanel p2;
		private JButton send;
		private JButton clear;
		private JButton exit;
		private JComboBox<String> jcb;

		private MultiChatInfo info;

        MultiChatFrame(MultiChatInfo info) {
			super();
			this.info = info;

			((JPanel)this.getContentPane()).setOpaque(false);
			ImageIcon img = new ImageIcon("./images/qq14.jpg");
			JLabel background=new JLabel(img);
			this.getLayeredPane().add(background,new Integer(Integer.MIN_VALUE));
			background.setBounds(0, 0, img.getIconWidth(), img.getIconHeight());
			setTitle("多人聊天(" + QQFrame.this.self.getNick() + ")");
			setLayout(new BorderLayout());
			Font font = new Font("Helvetica Bold", Font.BOLD, 16);

			// 消息显示框
			text1 = new JTextArea(15, 50);
			text1.setLineWrap(true);    // 设置自动换行
			text1.setEditable(false);   // 禁止用户编辑
			text1.setFont(font);
			text1.setOpaque(false);
			jp_up = new JScrollPane(text1);    // 给文本域增加滚动条
			jp_up.getViewport().setOpaque(false);
			jp_up.setOpaque(false);
			p1 = new JPanel();
			p1.setOpaque(false);
			p1.add(jp_up);
			add(p1, BorderLayout.CENTER);

			JPanel temp = new JPanel();
			temp.setOpaque(false);
			temp.setLayout(new BorderLayout());
			JPanel tup = new JPanel();
			tup.setOpaque(false);
			tup.setLayout(new FlowLayout());
			clear = new JButton("清屏");
			clear.setFont(font);
			jcb = new JComboBox<>();
			jcb.addItem("黑色");
			jcb.addItem("蓝色");
			jcb.addItem("红色");
			jcb.setFont(font);
			tup.add(jcb);
			tup.add(clear);
			temp.add(tup, BorderLayout.NORTH);

			// 消息输入框
			text2 = new JTextArea(8, 50);
			text2.setLineWrap(true);    // 设置自动换行
			text2.setFont(font);
			text2.setOpaque(false);
			jp_down = new JScrollPane(text2); // 给文本域增加滚动条
			jp_down.setOpaque(false);
			jp_down.getViewport().setOpaque(false);
			p2 = new JPanel();
			p2.setOpaque(false);
			p2.add(jp_down);
			temp.add(p2, BorderLayout.CENTER);

			// 发送，清屏按钮
			send = new JButton("发送");
			send.setFont(font);
			exit = new JButton("退出");
			exit.setFont(font);
			tup = new JPanel();
			tup.setOpaque(false);
			tup.setLayout(new FlowLayout());
			tup.add(send);
			tup.add(exit);
			temp.add(tup, BorderLayout.SOUTH);
			add(temp, BorderLayout.SOUTH);

			JPanel left = (JPanel) this.getContentPane();
			JPanel pane = new JPanel();
			pane.setOpaque(false);
			pane.setLayout(new BorderLayout());
			pane.add(left, BorderLayout.CENTER);
			JScrollPane right = new JScrollPane();
			right.setOpaque(false);
			list = new JList<>();
			list.setOpaque(false);
			list.setCellRenderer(new ListItem());
			list.setListData(info.getPeople());
			right.getViewport().add(list);
			right.getViewport().setOpaque(false);
			pane.add(right, BorderLayout.EAST);
			pane.setOpaque(false);
			this.setContentPane(pane);
			pack();
			// 获取屏幕大小
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			// 将窗体居中
			setLocation(screen.width/2-this.getWidth()/2, screen.height/2-this.getHeight()/2);
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			
			// 设置观感
			String plaf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
			try {
				UIManager.setLookAndFeel(plaf);
				SwingUtilities.updateComponentTreeUI(this);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			event();
			setResizable(false);
			setVisible(true);
		}

		private class ListItem extends JLabel implements ListCellRenderer<String> {
			private Border
					selectedBorder = BorderFactory.createLineBorder(Color.blue,1),
					emptyBorder = BorderFactory.createEmptyBorder(1,1,1,1);
			private Font font = new Font(Font.SERIF, Font.PLAIN, 20);
			public Component getListCellRendererComponent(JList<? extends String> list,
														  String val, int index, boolean isSelected, boolean cellHasFocus) {
				this.setFont(font);
				this.setText(val);
				if ( isSelected ) {
					setBorder (selectedBorder);
				}
				else {
					setBorder(emptyBorder);
				}
				this.setOpaque(false);
				return this;
			}
		}
		// 添加事件
		private void event() {
			
			this.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					try {
						oos.writeUTF("reject multiChat");
						oos.flush();
						oos.writeInt(info.getId());
						oos.flush();
					} catch (IOException e1) {
						return;
					}
				}
			});
			// 发送按钮点击事件
			send.addActionListener(e -> sendMessage());
			
			// 清屏
			clear.addActionListener(e -> {
                text1.setText("");
                text2.setText("");
            });
			
			// 键盘事件
			KeyListener key = new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					if(e.getKeyCode() == KeyEvent.VK_ESCAPE) {
						MultiChatFrame.this.dispose();
					}
					else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
						MultiChatFrame.this.sendMessage();
					}
				}
			};
			this.addKeyListener(key);
			send.addKeyListener(key);
			clear.addKeyListener(key);
			text1.addKeyListener(key);
			text2.addKeyListener(key);
			list.addKeyListener(key);
			jcb.addKeyListener(key);
			exit.addKeyListener(key);

			jcb.addActionListener((ActionEvent e) -> {
				String s = jcb.getItemAt(jcb.getSelectedIndex());
				Color color;
				if(s.equals("黑色")) color = Color.BLACK;
				else if(s.equals("蓝色")) color = Color.BLUE;
				else color = Color.RED;
				text1.setForeground(color);
				text2.setForeground(color);
			});

			exit.addActionListener(e -> {
				MultiChatFrame.this.dispose();
				try {
					oos.writeUTF("reject multiChat");
					oos.flush();
					oos.writeInt(info.getId());
					oos.flush();
				} catch (IOException e1) {
					return;
				}
			});

			list.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if(e.getClickCount() == 2) {
						String cur = list.getSelectedValue();
						if(cur.equals(QQFrame.this.self.getNick())) return;
						for(ChatFrame tmp : cf) {
							if(tmp.title.equals(cur)) {
								return;
							}
						}
						ChatFrame tmp = new ChatFrame(cur);
						cf.addElement(tmp);
					}
				}
			});
		}
		
		// 发送群聊消息
		private void sendMessage() {
			Date cur = new Date();
			Message message = new Message(text2.getText(), cur);

			if (message.getMessage().equals("")) {
				JOptionPane.showMessageDialog(this, "消息不能为空", "提示", JOptionPane.WARNING_MESSAGE);
				return;
			}

			try {
				oos.writeUTF("multiChat");
				oos.flush();
				oos.writeInt(this.info.getId());
				oos.flush();
				oos.writeObject(message);
				oos.flush();
				text1.append(message.getDate() + " 我说:\n" + message.getMessage() + "\r\n\r\n");
				// 将光标设置为行末，以实现滚动面板自动往下移动
				text1.setCaretPosition(text1.getText().length());
				text2.setText("");
			} catch (IOException ignored) {
			}
		}
	}

	// 群聊选择窗口
	class QLSelector extends JFrame {
		private JButton sure = new JButton("确定");
		private JButton cancel = new JButton("取消");
		private final Font font = new Font(Font.SERIF, Font.PLAIN, 20);
		private Vector<String> select;
		private Vector<JCheckBox> box;
		QLSelector(Vector<String> select) {
			this.select = select;
			this.box = new Vector<>();
			this.setTitle("选择群聊对象");
			this.setLayout(new BorderLayout());
			JPanel up = new JPanel();
			up.setLayout(new GridLayout((this.select.size()+1)/2, 2));
			for(String s : this.select) {
				JCheckBox temp = new JCheckBox(s);
				temp.setFont(font);
				box.addElement(temp);
				up.add(temp);
			}
			add(up, BorderLayout.CENTER);
			JPanel down = new JPanel();
			down.setLayout(new FlowLayout());
			sure.setFont(font);
			down.add(sure);
			cancel.setFont(font);
			down.add(cancel);
			add(down, BorderLayout.SOUTH);
			pack();
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

			sure.addActionListener(e -> {
				Vector<String> people = new Vector<>();
				for(JCheckBox bx : box) {
					if(bx.isSelected()) {
						people.addElement(bx.getText());
					}
				}
				people.addElement(QQFrame.this.self.getNick());
				try {
					oos.writeUTF("new multiChat");
					oos.flush();
					oos.writeObject(people);
					oos.flush();
				} catch (IOException e1) {
					return;
				}
				dispose();
			});
			cancel.addActionListener(e -> {
				dispose();
			});

			// 将窗体居中
			setLocation(QQFrame.this.getX()+QQFrame.this.getWidth()/2-this.getWidth()/2, QQFrame.this.getY()+QQFrame.this.getHeight()/2-this.getHeight()/2);
			setVisible(true);
		}
	}
}
