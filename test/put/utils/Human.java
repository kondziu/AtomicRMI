package put.utils;

import java.util.HashMap;
import java.util.Map;

public class Human {
	private static Human dict = null;
	private Map<Object, Integer> sequences = new HashMap<>();
	private Map<Object, Map<Object, Integer>> dicts = new HashMap<>();

	public static Human ize() {
		if (dict == null) {
			dict = new Human();
		}
		return dict;
	}

	public String me(String section, Object id) {
		synchronized (dict) {
			Map<Object, Integer> map = dicts.get(section);
			if (map == null) {
				map = new HashMap<>();
				dicts.put(section, map);
			}
			
			Integer sequence = sequences.get(section);
			if (sequence == null) {
				sequence = 0;
			}

			Integer i = map.get(id);
			if (i == null) {
				map.put(id, ++sequence);
				i = map.get(id);
			}
			
			sequences.put(section, sequence);
			return section + i;
		}
	}
}
