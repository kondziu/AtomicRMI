package put.utils;

import java.util.HashMap;
import java.util.Map;

public class UniversalTranslator {
	public static final Map<Object, Object> map = new HashMap<Object, Object>();
	public static final Map<Object, Object> reverse = new HashMap<Object, Object>();

	public static final <T1, T2> void add(T1 key, T2 value) {
		synchronized (map) {
			map.put(key, value);
			reverse.put(value, key);
		}
	}

	@SuppressWarnings("unchecked")
	public static final <T1, T2> T2 byKey(T1 key) {
		return (T2) map.get(key);
	}

	@SuppressWarnings("unchecked")
	public static final <T1, T2> T1 byValue(T2 value) {
		return (T1) reverse.get(value);
	}
}
