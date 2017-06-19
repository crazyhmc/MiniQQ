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
	private JButton ql = new JButton("�½�Ⱥ��");
	private User self;
	private JList<String> list;
	private Vector<ChatFrame> cf;
	private Vector<MultiChatFrame> mcf;
	private static final String SERVER_IP = "127.0.0.1";
	
	// ��¼������
	private Socket socket;
	private ObjectInputStream ois;
	private ObjectOutputStream oos;

	// �ļ����䲿��
	private Socket fileSocket;
	private ObjectInputStream filein;
	private ObjectOutputStream fileout;

	QQFrame(Socket ss, ObjectInputStream in, ObjectOutputStream out, User self, Vector<Friend> curFr) {
		// QQFrame�����ʼ��
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

		// �ļ����ֳ�ʼ��
		try {
			this.fileSocket = new Socket(SERVER_IP, 9000);
			this.filein = new ObjectInputStream(this.fileSocket.getInputStream());
			this.fileout = new ObjectOutputStream(this.fileSocket.getOutputStream());
			this.fileout.writeObject(this.self);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// ���ñ��⣬���ֹ��������Լ�ȫ������
		setTitle("MyQQ");
		((JPanel)this.getContentPane()).setOpaque(false);
		ImageIcon img = new ImageIcon("./images/qq6.jpg");
		JLabel background=new JLabel(img);
		this.getLayeredPane().add(background,new Integer(Integer.MIN_VALUE));
		background.setBounds(0, 0, img.getIconWidth(), img.getIconHeight());
		setLayout(new BorderLayout());
		Font font = new Font(Font.SERIF, Font.PLAIN, 20);
		
		// ���������б�
		list = new JList<>();
		list.setCellRenderer(new listItem());
		list.setListData(arr);
		list.setOpaque(false);
		JScrollPane jsp = new JScrollPane();
		jsp.getViewport().setOpaque(false);

		jsp.getViewport().add(list);
		add(jsp, BorderLayout.CENTER);
		
		// ����������Ϣ���
		JPanel pane = new JPanel();
		pane.setOpaque(false);
		pane.setLayout(new GridLayout(2, 2));
		JLabel nick = new JLabel("�ǳ�");
		pane.add(nick);
		nick.setFont(font);
		JLabel nickValue = new JLabel(this.self.getNick());
		nickValue.setFont(font);
		pane.add(nickValue);

		JLabel sex = new JLabel("�Ա�");
		sex.setFont(font);
		pane.add(sex);
		JLabel sexValue = new JLabel(this.self.getSex().equals("male") ? "��" : "Ů");
		sexValue.setFont(font);
		pane.add(sexValue);
		
		add(pane, BorderLayout.NORTH);
		setSize(350, 800);

		ql.setFont(new Font(Font.SERIF, Font.PLAIN, 15));
		add(ql, BorderLayout.SOUTH);
		setResizable(false);
		// ��ȡ��Ļ��С
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		// ���������
		setLocation(screen.width/2-175, screen.height/2-400);
		setVisible(true);
		
		// ����¼�
		event();
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		
		// ���ù۸�
		String plaf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
		try {
			UIManager.setLookAndFeel(plaf);
			SwingUtilities.updateComponentTreeUI(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// �����µ��߳�����������н���
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
					// �˳�ǰ��֪������
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
		
		// Ϊ�б�����¼�����
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if(e.getClickCount() == 2) {
					String nickname = list.getSelectedValue();
					// �����Է������촰���Ƿ����
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
		
		// ΪȺ�İ�ť����¼�
		ql.addActionListener(e -> {
			if(arr.size() == 0) {
				JOptionPane.showMessageDialog(QQFrame.this, "��ǰ��������Ϊ0���޷�����Ⱥ��", "Sorry", JOptionPane.PLAIN_MESSAGE);
				return;
			}
			new QLSelector(arr);
		});

	}
	
	// �����������
	private void exchange() {
		// �������շ�������Ӧ���ַ���
		String doc;
		
		while(true) {
			try {
				// ���������ǿ�ƴ�ϣ�������IOException
				doc = ois.readUTF();
				// ����������ʱ�����������б�
				switch (doc) {
					case "one offline":
						Friend fr = (Friend) ois.readObject();
						curFr.removeElement(fr);
						arr.removeElement(fr.getNick());
						list.setListData(arr);
						break;
					// ���������ߣ����������б�
					case "one online":
						Friend newFr = (Friend) ois.readObject();
						curFr.addElement(newFr);
						arr.addElement(newFr.getNick());
						list.setListData(arr);
						break;
					// ��������Ϣʱ���Է�ǡ�����ߣ�������Ϣʧ�ܣ��򵯴���֪�û�
					case "The opposite side is offline": {
						String nick = ois.readUTF();
						JFrame parent = this;
						for (ChatFrame aCf : this.cf) {
							if (aCf.title.equals(nick)) {
								parent = aCf;
								break;
							}
						}
						JOptionPane.showMessageDialog(parent, nick + "�����ߣ���Ϣ����ʧ��", "��ʾ", JOptionPane.WARNING_MESSAGE);
						break;
					}
					// �����������죬���նԷ�������Ϣ
					case "singleChat": {
						String nick = ois.readUTF();
						Message message = (Message) ois.readObject();
						boolean flag = false;    // ������ǣ�������ʾ��ǰ��Է������촰���Ƿ����

						// �����Է������촰���Ƿ���ڣ���������±�ǣ���������Ϣ��
						for (int i = 0; i < this.cf.size() && !flag; i++) {
							ChatFrame temp = this.cf.get(i);
							if (temp.title.equals(nick)) {
								temp.text1.append(message.getDate() + " " + nick + "˵\n" + message.getMessage() + "\r\n\r\n");
								// ���������Ϊ��ĩ����ʵ�ֹ�������Զ������ƶ�
								temp.text1.setCaretPosition(temp.text1.getText().length());
								flag = true;
							}
						}
						// ������������½�����
						if (!flag) {
							String[] options = {"����", "���"};
							int res = JOptionPane.showOptionDialog(this, nick + "������������", "��ʾ", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
							if (res == 0) {
								ChatFrame temp = new ChatFrame(nick);
								temp.text1.append(message.getDate() + " " + nick + "˵\n" + message.getMessage() + "\r\n\r\n");
								// ���������Ϊ��ĩ����ʵ�ֹ�������Զ������ƶ�
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
						JOptionPane.showMessageDialog(temp, nick + "�ܾ�����˵��!", "Sorry", JOptionPane.PLAIN_MESSAGE);
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
						String[] options = {"����", "���" };
						int val = JOptionPane.showOptionDialog(this, nick + "�����˶�������", "��ʾ", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
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
						// ���������촰��д��Ϣ
						for(MultiChatFrame cur : this.mcf) {
							if(cur.info.getId() == id) {
								cur.text1.append(nick + "�뿪�˸ö�������\n\n");
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
								cur.text1.append(message.getDate() + " " + nick + "˵\n" + message.getMessage() + "\r\n\r\n");
								// ���������Ϊ��ĩ����ʵ�ֹ�������Զ������ƶ�
								cur.text1.setCaretPosition(cur.text1.getText().length());
								break;
							}
						}
						break;
					}
				}
			} catch (IOException e) {
				// ����ǰ����������б�
				arr.removeAllElements();
				list.setListData(arr);
				return;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			
		}
	}

	// ���ļ�����˽��н���
	private void chatWithFileServer() {
		// ��ȡָ��
		String cmd;
		// ��ȡ�ǳ�
		String nick;
		// ���ܳ��ֵĵ����ĸ�����
		JFrame parent;
		try {
			while(true) {
				cmd = filein.readUTF();
				nick = filein.readUTF();
				// ���������Է������촰�ڣ��򸸴���Ϊ�����촰�ڣ�����ΪQQ����
				parent = this;
				for (ChatFrame cur : this.cf) {
					if(cur.title.equals(nick)) {
						parent = cur;
						break;
					}
				}
				switch (cmd) {
					case "offline_fileTrans" :
					// �����ļ���˵�����˷����ļ�
					case "online_fileTrans" : {
						// ��ȡ�ļ��ڷ������˵�·�������������ļ�
						File file = (File)filein.readObject();
						// ��ȡ�ļ������Է����û�֪���ļ��������Լ���ʽ
						String filename = filein.readUTF();
						String[] options = {"����", "���"};
						int val = JOptionPane.showOptionDialog(parent, nick + "���������ļ� " + filename, "��ʾ", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
						if (val == 0) {
							JFileChooser chooser = new JFileChooser();
							chooser.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
							int res = chooser.showSaveDialog(parent);
							if(res == JFileChooser.APPROVE_OPTION) {
								// ֪ͨ��������֪�Է���ͬ����նԷ��ļ������÷������ȴ�������ɵ�ָ��
								fileout.writeUTF("accept");
								fileout.flush();
								fileout.writeUTF(nick);
								fileout.flush();
								fileout.writeObject(file);
								fileout.flush();

								// ����������ļ�
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
								// ��֪��������ɽ���
								fileout.writeUTF("ok");
								fileout.flush();
							}
							else {
								// ����
								fileout.writeUTF("reject");
								fileout.flush();
								fileout.writeUTF(nick);
								fileout.flush();
								fileout.writeObject(file);
								fileout.flush();
							}
						}
						else {
							// ����
							fileout.writeUTF("reject");
							fileout.flush();
							fileout.writeUTF(nick);
							fileout.flush();
							fileout.writeObject(file);
							fileout.flush();
						}
						break;
					}
					// �õ��Է��Ѿ��ɹ����յ�ָ��
					case "has_accepted" : {
						JOptionPane.showMessageDialog(parent, nick + "�ѳɹ���������ļ�", "��ϲ", JOptionPane.PLAIN_MESSAGE);
						break;
					}
					// �õ��Է����յ�ָ��
					case "has_rejected" : {
						JOptionPane.showMessageDialog(parent, nick + "�ܾ���������ļ�", "Sorry", JOptionPane.PLAIN_MESSAGE);
						break;
					}
					// �õ��Է����ߵ�ָ��
					case "opposite side is offline" : {
						JOptionPane.showMessageDialog(parent, nick + "�����ߣ��ļ�����ʧ��", "Sorry", JOptionPane.PLAIN_MESSAGE);
						break;
					}
					case "upload successfully" : {
						JOptionPane.showMessageDialog(parent, "�����ļ����ϴ������������ȴ��Է����߽���", "��ʾ", JOptionPane.PLAIN_MESSAGE);
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

	// ���촰��
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

			// ��Ϣ��ʾ��
			text1 = new JTextArea(15, 50);
			text1.setLineWrap(true);    // �����Զ�����
			text1.setEditable(false);   // ��ֹ�û��༭
			text1.setFont(font);
			text1.setOpaque(false);
			jp_up = new JScrollPane(text1);    // ���ı������ӹ�����
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
			file_send = new JButton("�ļ�����");
			file_send.setFont(font);
			clear = new JButton("����");
			clear.setFont(font);
			jcb = new JComboBox<>();
			jcb.addItem("��ɫ");
			jcb.addItem("��ɫ");
			jcb.addItem("��ɫ");
			jcb.setFont(font);
			tup.add(jcb);
			tup.add(file_send);
			tup.add(clear);
			temp.add(tup, BorderLayout.NORTH);

			// ��Ϣ�����
			text2 = new JTextArea(8, 50);
			text2.setLineWrap(true);    // �����Զ�����
			text2.setFont(font);
			text2.setOpaque(false);
			jp_down = new JScrollPane(text2); // ���ı������ӹ�����
			jp_down.setOpaque(false);
			jp_down.getViewport().setOpaque(false);
			p2 = new JPanel();
			p2.setOpaque(false);
			p2.add(jp_down);
			temp.add(p2, BorderLayout.CENTER);

			// ���ͣ�������ť
			send = new JButton("����");
			send.setFont(font);
			exit = new JButton("�˳�");
			exit.setFont(font);
			tup = new JPanel();
			tup.setOpaque(false);
			tup.setLayout(new FlowLayout());
			tup.add(send);
			tup.add(exit);
			temp.add(tup, BorderLayout.SOUTH);
			add(temp, BorderLayout.SOUTH);
			pack();
			// ��ȡ��Ļ��С
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			// ���������
			setLocation(screen.width / 2 - this.getWidth() / 2, screen.height / 2 - this.getHeight() / 2);
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);


			// ���ù۸�
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

		// Ϊ�������¼�����
		private void event() {
			send.addActionListener(event -> ChatFrame.this.sendMessage());

			// ����
			this.clear.addActionListener(e -> {
				ChatFrame.this.text1.setText("");
				ChatFrame.this.text2.setText("");
			});

			// �����¼�
			KeyListener key = new KeyAdapter() {

				@Override
				public void keyPressed(KeyEvent e) {
					// ESC �ر����촰��
					if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
						ChatFrame.this.dispose();
						QQFrame.this.cf.removeElement(ChatFrame.this);
					}
					// Ctrl + Enter Ϊ������Ϣ
					else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
						ChatFrame.this.sendMessage();
					}
				}
			};
			// Ϊ���������Ӽ����¼�
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
				if(s.equals("��ɫ")) color = Color.BLACK;
				else if(s.equals("��ɫ")) color = Color.BLUE;
				else color = Color.RED;
				text1.setForeground(color);
				text2.setForeground(color);
			});

			// �ļ������¼�
			this.file_send.addActionListener(event -> {
				String[] options = {"���ߴ���", "���ߴ���"};
				int val = JOptionPane.showOptionDialog(ChatFrame.this, "��ѡ���䷽ʽ", "����", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
				if(val == 0)
					new Thread(() -> ChatFrame.this.sendFile("online_fileTrans")).start();
				else if(val == 1)
					new Thread(() -> ChatFrame.this.sendFile("offline_fileTrans")).start();
			});
			// �ر�ʱ�������������Ƴ�
			this.addWindowListener(new WindowAdapter() {

				@Override
				public void windowClosing(WindowEvent e) {
					QQFrame.this.cf.removeElement(ChatFrame.this);
				}
			});
		}

		// ������Ϣ
		private void sendMessage() {
			Date cur = new Date();
			Message message = new Message(text2.getText(), cur);

			if (message.getMessage().equals("")) {
				JOptionPane.showMessageDialog(this, "��Ϣ����Ϊ��", "��ʾ", JOptionPane.WARNING_MESSAGE);
				return;
			}

			try {
				oos.writeUTF("singleChat");
				oos.flush();
				oos.writeUTF(this.title);
				oos.flush();
				oos.writeObject(message);
				oos.flush();
				text1.append(message.getDate() + " ��˵:\n" + message.getMessage() + "\r\n\r\n");
				// ���������Ϊ��ĩ����ʵ�ֹ�������Զ������ƶ�
				text1.setCaretPosition(text1.getText().length());
				text2.setText("");
			} catch (IOException ignored) {
			}
		}

		// �����ļ�
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

	// �������촰��
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
			setTitle("��������(" + QQFrame.this.self.getNick() + ")");
			setLayout(new BorderLayout());
			Font font = new Font("Helvetica Bold", Font.BOLD, 16);

			// ��Ϣ��ʾ��
			text1 = new JTextArea(15, 50);
			text1.setLineWrap(true);    // �����Զ�����
			text1.setEditable(false);   // ��ֹ�û��༭
			text1.setFont(font);
			text1.setOpaque(false);
			jp_up = new JScrollPane(text1);    // ���ı������ӹ�����
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
			clear = new JButton("����");
			clear.setFont(font);
			jcb = new JComboBox<>();
			jcb.addItem("��ɫ");
			jcb.addItem("��ɫ");
			jcb.addItem("��ɫ");
			jcb.setFont(font);
			tup.add(jcb);
			tup.add(clear);
			temp.add(tup, BorderLayout.NORTH);

			// ��Ϣ�����
			text2 = new JTextArea(8, 50);
			text2.setLineWrap(true);    // �����Զ�����
			text2.setFont(font);
			text2.setOpaque(false);
			jp_down = new JScrollPane(text2); // ���ı������ӹ�����
			jp_down.setOpaque(false);
			jp_down.getViewport().setOpaque(false);
			p2 = new JPanel();
			p2.setOpaque(false);
			p2.add(jp_down);
			temp.add(p2, BorderLayout.CENTER);

			// ���ͣ�������ť
			send = new JButton("����");
			send.setFont(font);
			exit = new JButton("�˳�");
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
			// ��ȡ��Ļ��С
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			// ���������
			setLocation(screen.width/2-this.getWidth()/2, screen.height/2-this.getHeight()/2);
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			
			// ���ù۸�
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
		// ����¼�
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
			// ���Ͱ�ť����¼�
			send.addActionListener(e -> sendMessage());
			
			// ����
			clear.addActionListener(e -> {
                text1.setText("");
                text2.setText("");
            });
			
			// �����¼�
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
				if(s.equals("��ɫ")) color = Color.BLACK;
				else if(s.equals("��ɫ")) color = Color.BLUE;
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
		
		// ����Ⱥ����Ϣ
		private void sendMessage() {
			Date cur = new Date();
			Message message = new Message(text2.getText(), cur);

			if (message.getMessage().equals("")) {
				JOptionPane.showMessageDialog(this, "��Ϣ����Ϊ��", "��ʾ", JOptionPane.WARNING_MESSAGE);
				return;
			}

			try {
				oos.writeUTF("multiChat");
				oos.flush();
				oos.writeInt(this.info.getId());
				oos.flush();
				oos.writeObject(message);
				oos.flush();
				text1.append(message.getDate() + " ��˵:\n" + message.getMessage() + "\r\n\r\n");
				// ���������Ϊ��ĩ����ʵ�ֹ�������Զ������ƶ�
				text1.setCaretPosition(text1.getText().length());
				text2.setText("");
			} catch (IOException ignored) {
			}
		}
	}

	// Ⱥ��ѡ�񴰿�
	class QLSelector extends JFrame {
		private JButton sure = new JButton("ȷ��");
		private JButton cancel = new JButton("ȡ��");
		private final Font font = new Font(Font.SERIF, Font.PLAIN, 20);
		private Vector<String> select;
		private Vector<JCheckBox> box;
		QLSelector(Vector<String> select) {
			this.select = select;
			this.box = new Vector<>();
			this.setTitle("ѡ��Ⱥ�Ķ���");
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

			// ���������
			setLocation(QQFrame.this.getX()+QQFrame.this.getWidth()/2-this.getWidth()/2, QQFrame.this.getY()+QQFrame.this.getHeight()/2-this.getHeight()/2);
			setVisible(true);
		}
	}
}
