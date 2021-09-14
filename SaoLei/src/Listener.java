import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class Listener implements ActionListener{
	public SaoLei saolei;
	public Listener(SaoLei saolei) {
		// TODO Auto-generated constructor stub
		this.saolei = saolei;
		for(int i = 0; i < saolei.MaxSquare; i++) {
			for(int j = 0; j < saolei.MaxSquare; j++) {
				int x = i;
				int y = j;
				saolei.jButtons[i][j].addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {// 鼠标单击事件
						int c = e.getButton();// 得到按下的鼠标键
						if (c == MouseEvent.BUTTON1){ // 判断是鼠标左键按下
							saolei.Buttonstate[x][y].LeftClick();
						}
						if (c == MouseEvent.BUTTON3) {// 判断是鼠标右键按下
							saolei.Buttonstate[x][y].RightClick();
						}
						saolei.jButtons[x][y].setBackground(saolei.Buttonstate[x][y].getColor());
						if(saolei.Buttonstate[x][y].is_Click == 1 && saolei.Buttonstate[x][y].getColor().equals(new Color(255, 0, 0))) {
							JOptionPane.showMessageDialog(null, "You are loser");  
						}
						if(saolei.Buttonstate[x][y].is_Lei == 0 && saolei.Buttonstate[x][y].is_Click == 1) {
							int sum = 0;
							for(int k = 0; k < 3; k++) {
								for(int t = 0; t < 3; t++) {
									if(x - 1 + k < 0 || x - 1 + k >= saolei.MaxSquare) {
										continue;
									}
									if(y - 1 + t < 0 || y - 1 + t >= saolei.MaxSquare) {
										continue;
									}
									if(saolei.Buttonstate[x - 1 + k][y - 1 + t].is_Lei == 1) {
										sum++;
									}
								}
							}
							if(sum != 0) {
								saolei.jButtons[x][y].setText(String.valueOf(sum));
								saolei.jButtons[x][y].validate();
							}
							else {
								saolei.recursion(x, y);
							}
						}
						boolean judge = true;
						for(int k = 0; k < saolei.MaxSquare; k++) {
							for(int t = 0; t < saolei.MaxSquare; t++) {
								if(saolei.Buttonstate[k][t].is_Click == 0 && saolei.Buttonstate[k][t].is_Lei == 0) {
									judge = false;
								}
							}
						}
						if(judge) {
							JOptionPane.showMessageDialog(null, "You are winner");  
						}
					}
				});
				
			}
		}
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		JButton button = (JButton) e.getSource();
		if(button.getText().equals("确认")) {
			saolei.setButtonstate();
			saolei.cardLayout.show(saolei.SaoLeiPanel, "GamePanel");
		}
		else if(button.getText().equals("重新开始")) {
			saolei.cardLayout.show(saolei.SaoLeiPanel, "ExplainPanel");
			for(int i = 0; i < saolei.MaxSquare; i++) {
				for(int j = 0; j < saolei.MaxSquare; j++) {
					saolei.jButtons[i][j].setText("");
					saolei.jButtons[i][j].setBackground(new Color(125, 125, 125));
					saolei.Buttonstate[i][j].is_Click = 0;
					saolei.Buttonstate[i][j].is_Lei = 0;
					saolei.Buttonstate[i][j].is_set = 0;
				}
			}
			//saolei.setButtonstate();
			saolei.SaoLeiPanel.validate();
		}
	}
	
}
