package put.atomicrmi.test.refcell;

public interface Cell extends java.io.Serializable {
	void set(int v);

	int get();
}