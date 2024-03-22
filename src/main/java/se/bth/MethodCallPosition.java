package se.bth;

public class MethodCallPosition implements Comparable<MethodCallPosition>{
	private int beginLine;
	private int endLine;
	
	public MethodCallPosition() {
		this.beginLine = -1;
		this.endLine = -1;
	}
	
	public MethodCallPosition(int bLine, int eLine) {
		this.beginLine = bLine;
		this.endLine = eLine;
	}
	public int getBeginLine() {
		return beginLine;
	}
	public void setBeginLine(int beginLine) {
		this.beginLine = beginLine;
	}
	public int getEndLine() {
		return endLine;
	}
	public void setEndLine(int endLine) {
		this.endLine = endLine;
	}
	
	@Override
	public String toString() {
		return this.getBeginLine() + " - " + this.getEndLine();
	}
	
	public boolean equals(MethodCallPosition p) {
		if (this.getBeginLine() == p.getBeginLine() && this.getEndLine() == p.getEndLine())
			return true;
		return false;
	}

	@Override
	public int compareTo(MethodCallPosition o) {
		if (this.getBeginLine() < o.getBeginLine())
			return -1;
		else if (this.getBeginLine() == o.getBeginLine() && this.getEndLine() < o.getEndLine())
			return -1;
		else if (this.getBeginLine() == o.getBeginLine() && this.getEndLine() > o.getEndLine())
			return 1;
		else if (this.getBeginLine() > o.getBeginLine())
			return 1;
		return 0;
	}

}
