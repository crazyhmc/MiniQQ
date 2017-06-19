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
		setTitle("您好，请登录");
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
	
	// 初始化
	private void init() {
		setLayout(new BorderLayout());
		JPanel blank = new JPanel();
		blank.setLayout(new BorderLayout());
		JLabel bg = new JLabel();
		bg.setIcon(new ImageIcon("./images/loginbg.jpg"));
		blank.add(bg, BorderLayout.CENTER);
		add(blank, BorderLayout.NORTH);
		Font font = new Font(Font.SERIF, Font.PLAIN, 20);
		
		// 账号密码输入框
		JPanel up = new JPanel();
		up.setLayout(new GridLayout(2,1));
		
		JPanel temp = new JPanel();
		temp.setLayout(new GridLayout(1, 2));
		JLabel user = new JLabel("用户名:");
		user.setFont(font);
		username = new JTextField(20);
		username.setFont(font);
		temp.add(user);
		temp.add(username);
		up.add(temp);
		
		temp = new JPanel();
		temp.setLayout(new GridLayout(1, 2));
		JLabel pass = new JLabel("密码:");
		pass.setFont(font);
		password = new JPasswordField(20);
		password.setFont(font);
		temp.add(pass);
		temp.add(password);
		up.add(temp);
		
		add(up, BorderLayout.CENTER);
		
		// 注册及登录按钮
		Font f = new Font(Font.SERIF, Font.PLAIN, 15);
		register = new JButton("注册");
		register.setFont(f);
		login = new JButton("登录");
		login.setFont(f);
		JPanel down = new JPanel();
		down.setLayout(new FlowLayout());
		down.add(register);
		down.add(login);
		add(down, BorderLayout.SOUTH);
		
		// 设置观感
		String plaf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
		try {
			UIManager.setLookAndFeel(plaf);
			SwingUtilities.updateComponentTreeUI(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// 添加监听器
	private void event() {
		// 为注册按钮添加监听器
		register.addActionListener(e -> new Thread(RegisterFrame::new).start());
		
		// 为键盘添加事件监听
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
		
		// 为登录按钮注册监听器`
		login.addActionListener(e -> tryLogin());
	}
	
	private void tryLogin() {
		String user = username.getText();
		char[] ps = password.getPassword();
		String s = new String(ps);
		if(user.equals("")||s.equals("")) {
			JOptionPane.showMessageDialog(null, "用户名或密码为空！", "提示", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		try {
			// 连接服务器，并获取流对象
			socket = new Socket(SERVER_IP, 8888);
			ois = new ObjectInputStream(socket.getInputStream());
			oos = new ObjectOutputStream(socket.getOutputStream());
			
			// 创建本用户的User对象，并发往服务器验证
			us = new User(user, s, null, null); 
			oos.writeObject(us);
			oos.flush();
			
			// 接收服务器发回的回应
			String response = ois.readUTF();
			switch (response) {
				case "successfully login":
					// 获取完整本用户对象
					us = (User) ois.readObject();
					// 获取当前在线所有人的信息(昵称)
					Vector<Friend> curFr = (Vector<Friend>) ois.readObject();
					// 打开QQ窗口
					new QQFrame(socket, ois, oos, us, curFr);
					// 关闭登录窗口
					LoginFrame.this.dispose();
					break;
				// 如果本用户已经登录
				case "Had login":
					JOptionPane.showMessageDialog(this, "该用户已经登录", "提示", JOptionPane.WARNING_MESSAGE);
					username.setText("");
					password.setText("");

					oos.close();
					ois.close();
					socket.close();
					break;
				// 如果用户输入的信息有误
				default:
					JOptionPane.showMessageDialog(this, "用户名或密码不正确", "提示", JOptionPane.WARNING_MESSAGE);
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
