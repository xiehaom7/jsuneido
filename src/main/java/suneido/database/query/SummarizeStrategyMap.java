/* Copyright 2009 (c) Suneido Software Corp. All rights reserved.
 * Licensed under GPLv2.
 */

package suneido.database.query;

import static suneido.util.Util.startsWith;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import suneido.database.immudb.Record;
import suneido.database.query.Query.Dir;
import suneido.database.query.Summarize.Summary;

/**
 * accumulate results in memory
 * doesn't require any order, can only supply in order of "by"
 */
public class SummarizeStrategyMap extends SummarizeStrategy {
	TreeMap<Record, List<Summary>> results = new TreeMap<>();
	NavigableMap<Record, List<Summary>> selected;
	Map.Entry<Record, List<Summary>> cur;
	boolean first = true;

	SummarizeStrategyMap(Summarize source) {
		super(source);
	}

	@Override
	Row get(Dir dir, boolean rewound) {
		if (first) {
			process();
			first = false;
		}
		if (rewound) {
			selected = results.subMap(sel.org, true, sel.end, true);
			if (dir == Dir.NEXT)
				cur = selected.firstEntry();
			else
				cur = selected.lastEntry();
		}
		else if (dir == Dir.NEXT)
			cur = selected.higherEntry(cur.getKey());
		else // dir == PREV
			cur = selected.lowerEntry(cur.getKey());
		if (cur == null) {
			rewound = true;
			return null;
		}

		return makeRow(cur.getKey(), cur.getValue());
	}

	@Override
	void select(List<String> index, Record from, Record to) {
		assert startsWith(q.by, index); //TODO review if we need this
	}

	void process() {
		results.clear();
		Row row;
		while (null != (row = source.get(Dir.NEXT))) {
			Record byRec = row.project(q.getHdr(), q.by);
			List<Summary> sums = results.get(byRec);
			if (sums == null) {
				sums = funcSums();
				initSums(sums);
				results.put(byRec, sums);
			}
			for (int i = 0; i < sums.size(); ++i)
				sums.get(i).add(row.getval(q.getHdr(), q.on.get(i)));
		}
	}

}
