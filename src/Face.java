
public class Face {
	final int x;
	final int y;
	final int w;
	final int h;
	final Image img;
	final String name;
	
	public Face(int x, int y, int w, int h, String name, Image i) {
		this.x=x;
		this.y=y;
		this.w=w;
		this.h=h;
		this.name=name;
		this.img=i;
	}
	
	public String toString(){
		String s= name+"\nx:"+x+", y:"+y+", w:"+w+", h:"+h;
		//s+= "\nFrom bottom left corner:\nx:"+(x-w/2)+", y:"+(y-h/2)+", w:"+w+", h:"+h;
		return s;
	}
}
