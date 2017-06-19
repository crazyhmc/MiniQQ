package frame;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import objects.User;

public class RegisterFrame extends JFrame{

	private JTextField username;
	private JTextField nickname;
	private JPasswordField password;
	private JPasswordField surepassword;
	private JButton register;
	private JButton reset;
	private JRadioButton male;
	private JRadioButton female;

	private Socket socket;
	private ObjectInputStream ois;
	private ObjectOutputStream oop;
	
	RegisterFrame() {
		super();
		setTitle("Register");
		init();
		event();
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		pack();
		setResizable(false);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(screen.width/2-getWidth()/2,screen.height/2-getHeight()/2);
		setVisible(true);
	}
	
	// 初始化
	private void init() {
		((JPanel)this.getContentPane()).setOpaque(false);
		ImageIcon img = new ImageIcon("./images/qq4.jpg");
		JLabel background=new JLabel(img);
		this.getLayeredPane().setLayout(null);
		this.getLayeredPane().add(background, new Integer(Integer.MIN_VALUE));
		background.setBounds(0, 0, img.getIconWidth(), img.getIconHeight());

		setLayout(new GridLayout(6, 1));
		JLabel label;
		JPanel temp;
		Font font = new Font(Font.SERIF, Font.PLAIN, 20);
		
		// 输入昵称
		temp = new JPanel();
		temp.setOpaque(false);
		temp.setLayout(new GridLayout(1, 2));
		label = new JLabel("昵称:");
		label.setFont(font);
		temp.add(label, FlowLayout.LEFT);
		nickname = new JTextField(20);
		nickname.setFont(font);
		temp.add(nickname);
		add(temp);
		
		// 输入账号
		temp = new JPanel();
		temp.setOpaque(false);
		temp.setLayout(new GridLayout(1, 2));
		label = new JLabel("用户名:");
		label.setFont(font);
		temp.add(label, FlowLayout.LEFT);
		username = new JTextField(20);
		username.setFont(font);
		temp.add(username);
		add(temp);
		
		// 输入密码
		temp = new JPanel();
		temp.setOpaque(false);
		temp.setLayout(new GridLayout(1, 2));
		label = new JLabel("密码:");
		label.setFont(font);
		temp.add(label, FlowLayout.LEFT);
		password = new JPasswordField(20);
		password.setFont(font);
		temp.add(password);
		add(temp);
		
		// 确认密码
		temp = new JPanel();
		temp.setOpaque(false);
		temp.setLayout(new GridLayout(1, 2));
		label = new JLabel("确认密码:");
		label.setFont(font);
		temp.add(label, FlowLayout.LEFT);
		surepassword = new JPasswordField(20);
		surepassword.setFont(font);
		temp.add(surepassword);
		add(temp);
		
		// 输入性别
		temp = new JPanel();
		temp.setOpaque(false);
		temp.setLayout(new FlowLayout());
		male = new JRadioButton("男");
		male.setForeground(Color.CYAN);
		female = new JRadioButton("女");
		female.setForeground(Color.CYAN);
		male.setSelected(true);
		male.setFont(font);
		female.setSelected(false);
		female.setFont(font);
		ButtonGroup bg = new ButtonGroup();
		bg.add(male);
		bg.add(female);
		label = new JLabel("性别:");
		label.setForeground(Color.CYAN);
		label.setFont(font);
		temp.add(label);
		temp.add(male);
		temp.add(female);
		add(temp);
		
		// 提交及重置按钮
		temp = new JPanel();
		temp.setOpaque(false);
		temp.setLayout(new FlowLayout());
		register = new JButton("注册");
		reset = new JButton("重置");
		register.setFont(font);
		reset.setFont(font);
		temp.add(register);
		temp.add(reset);
		add(temp);
		
		// 设置观感
		String plaf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
		try {
			UIManager.setLookAndFeel(plaf);
			SwingUtilities.updateComponentTreeUI(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void reSet() {
		nickname.setText("");
		username.setText("");
		password.setText("");
		surepassword.setText("");
		male.setSelected(true);
	}
	
	// 添加监听器 
	private void event() {
		// 为重置按钮添加监听器
		reset.addActionListener(e -> reSet());
		
		
		register.addActionListener(e -> {
            String response;

            String nick = nickname.getText();
            String user = username.getText();
            String ps = new String(password.getPassword());
            String aps = new String(surepassword.getPassword());
            String sex;

            if(male.isSelected()) sex = "male";
            else sex = "female";

            if(user.equals("")) {
                JOptionPane.showMessageDialog(null, "昵称不能为空！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if(!ps.equals(aps)) {
                password.setText("");
                surepassword.setText("");

                JOptionPane.showMessageDialog(null, "两次输入密码不一致！", "提示", JOptionPane.WARNING_MESSAGE);
			} else {
                try {
                    socket = new Socket("localhost", 8890);
                    oop = new ObjectOutputStream(socket.getOutputStream());
                    ois = new ObjectInputStream(socket.getInputStream());

                    oop.writeUTF(user);
                    oop.flush();
                    response = ois.readUTF();
                    System.out.println(response);
                    if(response.equals("用户名已存在")) {
                        // 弹窗提示
                        JOptionPane.showMessageDialog(null, "用户名已存在，请重新输入", "提示", JOptionPane.WARNING_MESSAGE);
                        reSet();
					} else {
                        User u = new User(user, ps, nick, sex);
                        oop.writeObject(u);
                        oop.flush();

                        response = ois.readUTF();
                        System.out.println(response);
                        if(response.equals("注册成功"))
                            JOptionPane.showMessageDialog(null, "恭喜您，注册成功！", "提示", JOptionPane.WARNING_MESSAGE);
                        else
                            JOptionPane.showMessageDialog(null, "注册失败，请重试！", "提示", JOptionPane.WARNING_MESSAGE);
                        reSet();
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                } finally {
					try {
						ois.close();
						oop.close();
						socket.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
                }
            }
        });
	}
	
	public static void main(String[] args) {
		new RegisterFrame();
	}

}
