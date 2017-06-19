package frame;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Vector;

import javax.swing.*;

import objects.Friend;
import objects.User;

public class LoginFrame extends JFrame{

	private JTextField username;
	private JPasswordField password;
	private JButton register;
	private JButton login;

	private Socket socket;
	private User us;
	private ObjectInputStream ois;
	private ObjectOutputStream oos;
	private static final String SERVER_IP = "127.0.0.1";
	
	private LoginFrame() {
		super();
		setTitle("���ã����¼");
		init();
		event();
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setSize(520,450);
		setResizable(false);
		setIconImage(new ImageIcon("./images/icon.jpg").getImage());
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(screen.width/2-getWidth()/2, screen.height/2-getHeight()/2);
		setVisible(true);
	}
	
	// ��ʼ��
	private void init() {
		setLayout(new BorderLayout());
		JPanel blank = new JPanel();
		blank.setLayout(new BorderLayout());
		JLabel bg = new JLabel();
		bg.setIcon(new ImageIcon("./images/loginbg.jpg"));
		blank.add(bg, BorderLayout.CENTER);
		add(blank, BorderLayout.NORTH);
		Font font = new Font(Font.SERIF, Font.PLAIN, 20);
		
		// �˺����������
		JPanel up = new JPanel();
		up.setLayout(new GridLayout(2,1));
		
		JPanel temp = new JPanel();
		temp.setLayout(new GridLayout(1, 2));
		JLabel user = new JLabel("�û���:");
		user.setFont(font);
		username = new JTextField(20);
		username.setFont(font);
		temp.add(user);
		temp.add(username);
		up.add(temp);
		
		temp = new JPanel();
		temp.setLayout(new GridLayout(1, 2));
		JLabel pass = new JLabel("����:");
		pass.setFont(font);
		password = new JPasswordField(20);
		password.setFont(font);
		temp.add(pass);
		temp.add(password);
		up.add(temp);
		
		add(up, BorderLayout.CENTER);
		
		// ע�ἰ��¼��ť
		Font f = new Font(Font.SERIF, Font.PLAIN, 15);
		register = new JButton("ע��");
		register.setFont(f);
		login = new JButton("��¼");
		login.setFont(f);
		JPanel down = new JPanel();
		down.setLayout(new FlowLayout());
		down.add(register);
		down.add(login);
		add(down, BorderLayout.SOUTH);
		
		// ���ù۸�
		String plaf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
		try {
			UIManager.setLookAndFeel(plaf);
			SwingUtilities.updateComponentTreeUI(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// ��Ӽ�����
	private void event() {
		// Ϊע�ᰴť��Ӽ�����
		register.addActionListener(e -> new Thread(RegisterFrame::new).start());
		
		// Ϊ��������¼�����
		KeyListener t = new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ENTER) {
					tryLogin();
				}
			}
		};
		username.addKeyListener(t);
		password.addKeyListener(t);
		this.addKeyListener(t);
		login.addKeyListener(t);
		register.addKeyListener(t);
		
		// Ϊ��¼��ťע�������`
		login.addActionListener(e -> tryLogin());
	}
	
	private void tryLogin() {
		String user = username.getText();
		char[] ps = password.getPassword();
		String s = new String(ps);
		if(user.equals("")||s.equals("")) {
			JOptionPane.showMessageDialog(null, "�û���������Ϊ�գ�", "��ʾ", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		try {
			// ���ӷ�����������ȡ������
			socket = new Socket(SERVER_IP, 8888);
			ois = new ObjectInputStream(socket.getInputStream());
			oos = new ObjectOutputStream(socket.getOutputStream());
			
			// �������û���User���󣬲�������������֤
			us = new User(user, s, null, null); 
			oos.writeObject(us);
			oos.flush();
			
			// ���շ��������صĻ�Ӧ
			String response = ois.readUTF();
			switch (response) {
				case "successfully login":
					// ��ȡ�������û�����
					us = (User) ois.readObject();
					// ��ȡ��ǰ���������˵���Ϣ(�ǳ�)
					Vector<Friend> curFr = (Vector<Friend>) ois.readObject();
					// ��QQ����
					new QQFrame(socket, ois, oos, us, curFr);
					// �رյ�¼����
					LoginFrame.this.dispose();
					break;
				// ������û��Ѿ���¼
				case "Had login":
					JOptionPane.showMessageDialog(this, "���û��Ѿ���¼", "��ʾ", JOptionPane.WARNING_MESSAGE);
					username.setText("");
					password.setText("");

					oos.close();
					ois.close();
					socket.close();
					break;
				// ����û��������Ϣ����
				default:
					JOptionPane.showMessageDialog(this, "�û��������벻��ȷ", "��ʾ", JOptionPane.WARNING_MESSAGE);
					username.setText("");
					password.setText("");

					ois.close();
					oos.close();
					socket.close();
					break;
			}
		} catch (IOException | ClassNotFoundException e1) {
			e1.printStackTrace();
		} finally {
			ois = null;
			oos = null;
			socket = null;
		}
	}
	
	public static void main(String[] args) {
		new LoginFrame();
	}

}
