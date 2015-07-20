package put.util;

public class Pair<L, R> {

	public Pair(L left, R right) {
		assert (left != null);
		assert (right != null);

		this.left = left;
		this.right = right;
	}

	final private L left;
	final private R right;

	// public L getLeft() {
	// return left;
	// }
	//
	// public R getRight() {
	// return right;
	// }

	@Override
	public int hashCode() {
		return left.hashCode() + right.hashCode();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Pair) {
			Pair pair = (Pair) obj;
			return left.equals(pair.left) && right.equals(pair.right);
		}
		return false;
	}

}
