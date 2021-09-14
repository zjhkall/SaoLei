import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class SaoLei {
	JFrame jFrame;
	CardLayout cardLayout;
	JPanel SaoLeiPanel;
	JPanel explainPanel;
	JPanel[] explainPanels;
	JPanel gamePanel;
	JPanel gamePanel2;
	JPanel gamePanel3;
	JOptionPane explainOptionPane;
	JButton confirmButton;
	JButton[][] jButtons;
	Square[][] Buttonstate;
	JButton againButton;
	JLabel jLabel;
	JComboBox<String> jComboBox;
	final int MaxSquare = 15;
	public void setjFrame() {
		jFrame = new JFrame("扫雷");
		jFrame.setBounds(670, 250, 528, 620);
		SaoLeiPanel = new JPanel();
		cardLayout = new CardLayout();
		SaoLeiPanel.setLayout(cardLayout);
		SaoLeiPanel.add("ExplainPanel", explainPanel);
		SaoLeiPanel.add("GamePanel", gamePanel);
		SaoLeiPanel.setVisible(true);
		jFrame.add(SaoLeiPanel);
		jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jFrame.setVisible(true);
	}
	public void setLable() {
		jLabel = new JLabel("选择难度     ");
		jLabel.setSize(100, 50);
	}
	public void setJList() {
		String[] difficulty = {"简单", "中等", "困难", "噩梦", "地狱"};
		jComboBox = new JComboBox<String>(difficulty);
	}
	public void setexplainPanel() {
		explainPanel = new JPanel();
		explainPanels = new JPanel[3];
		confirmButton = new JButton("确认");
		for(int i = 0; i < 3; i++) {
			explainPanels[i] = new JPanel();
			explainPanels[i].setBackground(new Color(125, 125, 125));
		}
		explainPanel.setSize(510, 560);
		explainPanel.setLayout(new GridLayout(3, 1));
		explainPanels[1].add(jLabel);
		explainPanels[1].add(jComboBox);
		explainPanels[1].add(confirmButton);
		for(int i = 0; i < 3; i++) {
			explainPanel.add(explainPanels[i]);
		}
		explainPanel.setBackground(new Color(22));
		for(int i = 0; i < 3; i++) {
			explainPanels[i].setVisible(true);
		}
		explainPanel.setVisible(true);
	}
	public void setgamePanel2() {
		gamePanel2 = new JPanel();
		againButton = new JButton("重新开始");
		againButton.setPreferredSize(new Dimension(510, 40));
		againButton.setBackground(new Color(137, 207, 240));
		gamePanel2.add(againButton);
		gamePanel2.setPreferredSize(new Dimension(510, 50));
		gamePanel2.setBackground(new Color(125, 125, 125));
		gamePanel2.setVisible(true);
	}
	public void setgamePanel3() {
		gamePanel3 = new JPanel();
		gamePanel3.setPreferredSize(new Dimension(510, 510));
		gamePanel3.setLayout(new GridLayout(15, 15));
		jButtons = new JButton[MaxSquare][MaxSquare];
		for(int i = 0; i < MaxSquare; i++) {
			for(int j = 0; j < MaxSquare; j++) {
				jButtons[i][j] = new JButton();
				jButtons[i][j].setSize(34, 34);
				jButtons[i][j].setMargin(new java.awt.Insets(0,0,0,0));
				jButtons[i][j].setBackground(new Color(125, 125, 125));
				gamePanel3.add(jButtons[i][j]);
			}
		}
		gamePanel3.setVisible(true);
	}
	public void setgamePanel() {
		gamePanel = new JPanel();
		gamePanel.setBackground(new Color(125, 125, 125));
		gamePanel.setSize(510, 560);
		gamePanel.setLayout(new FlowLayout());
		gamePanel.add(gamePanel2);
		gamePanel.add(gamePanel3);
		gamePanel.setVisible(true);
	}
	public void setNewButton() {
		Buttonstate = new Square[15][15];
		for(int i = 0; i < MaxSquare; i++) {
			for(int j = 0; j < MaxSquare; j++) {
				Buttonstate[i][j] = new Square();
			}
		}
	}
	public void recursion(int x, int y) {	
		Buttonstate[x][y].LeftClick();
		jButtons[x][y].setBackground(Buttonstate[x][y].getColor());
		
		int sum = 0;
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 3; j++) {
				if(x - 1 + i < 0 || x - 1 + i >= MaxSquare) {
					continue;
				}
				if(y - 1 + j < 0 || y - 1 + j >= MaxSquare) {
					continue;
				}
				if(Buttonstate[x - 1 + i][y - 1 + j].is_Lei == 1) {
					sum++;
				}
				
			}
		}
		if(sum == 0) {
			for(int i = 0; i < 3; i++) {
				for(int j = 0; j < 3; j++) {
					if(x - 1 + i < 0 || x - 1 + i >= MaxSquare) {
						continue;
					}
					if(y - 1 + j < 0 || y - 1 + j >= MaxSquare) {
						continue;
					}
					if(Buttonstate[x - 1 + i][y - 1 + j].is_Click == 1 || Buttonstate[x - 1 + i][y - 1 + j].is_Lei == 1)
						continue;
					recursion(x - 1 + i, y - 1 + j);
				}
			}
		}
		else {
			jButtons[x][y].setText(String.valueOf(sum));
		}
	}

	public void setButtonstate() {
		
		int MaxLei = 0;
		String string= jComboBox.getItemAt(jComboBox.getSelectedIndex());
		if(string.equals("简单")) {
			MaxLei = 10;
		}
		else if(string.equals("中等")) {
			MaxLei = 50;
		}
		else if(string.equals("困难")) {
			MaxLei = 90;
		}
		else if(string.equals("噩梦")) {
			MaxLei = 150;
		}
		else if(string.equals("地狱")) {
			MaxLei = 200;
		}
		int sum = 0;
		int x = 0, y = 0;
		//System.out.println(MaxLei);
		Random random = new Random();
		while(sum < MaxLei) {
			x = random.nextInt(MaxSquare);
			y = random.nextInt(MaxSquare);
			if(Buttonstate[x][y].is_set == 0) {
				Buttonstate[x][y].setLei();
			}
			else {
				sum--;
			}
			sum++;
		}
		
	}
	public SaoLei() {
		setLable();
		setJList();
		setNewButton();
		setexplainPanel();
		setgamePanel2();
		setgamePanel3();
		setgamePanel();
		setjFrame();
	}
	public void addListener() {
		Listener listener = new Listener(this);
		confirmButton.addActionListener(listener);
		againButton.addActionListener(listener);
	}
	public static void main(String[] args) {
		SaoLei saoLei = new SaoLei();
		saoLei.addListener();
	}
}	
