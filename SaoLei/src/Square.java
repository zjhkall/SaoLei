import java.awt.Color;

public class Square{
	public int is_Lei;
	public int is_Click;
	public int is_set;
	public Square() {
		this.is_Lei = 0;
		this.is_Click = 0;
		this.is_set = 0;
	}
	public void Set() {
		this.is_set = 1;
	}
	public void LeftClick() {
		is_Click = 1;
	}
	public void RightClick() {
		is_Click = -1;
	}
	public void setLei() {
		is_Lei = 1;
	}
	public Color getColor() {
		if(is_Click == 0) {
			return new Color(125, 125, 125);
		}
		else if(is_Click == 1) {
			if(is_Lei == 1) {
				return new Color(255, 0, 0);
			}
			else {
				return new Color(137, 207, 240);
			}
		}
		else{
			return new Color(255, 255, 0);
		}
	}
}
