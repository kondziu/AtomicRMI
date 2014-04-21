package put.sync;

import java.io.Serializable;
import java.util.Comparator;

public class CasObject<T> implements Serializable {

	private static final long serialVersionUID = 4228075154380554950L;

	private T value;

	public CasObject() {
	}

	public CasObject(T value) {
		this.value = value;
	}

	public synchronized T getValue() {
		return value;
	}

	public synchronized boolean compareAndSwap(T expectedValue, T newValue) {
		if (value.equals(expectedValue)) {
			value = newValue;
			return true;
		}
		return false;
	}

	public synchronized boolean compareAndSwap(T expectedValue, T newValue,
			Comparator<T> comparator) {
		if (comparator.compare(value, expectedValue) == 0) {
			value = newValue;
			return true;
		}
		return false;
	}
}
