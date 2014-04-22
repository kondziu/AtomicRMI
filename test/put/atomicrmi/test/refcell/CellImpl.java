package put.atomicrmi.test.refcell;

public class CellImpl implements Cell {

	private static final long serialVersionUID = 6501233346783794137L;

	private int contents = 0;

	public void set(int v) {
		contents = v;
	}

	public int get() {
		return contents;
	}
}
