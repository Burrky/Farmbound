package com.spyglassfarming.farming;

import com.spyglassfarming.DataRows;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

// The rows are data: resource farming/patch-states.tsv, derived from RuneLite's
// net.runelite.client.plugins.timetracking.farming.PatchImplementation
// (RuneLite master @ e98399f931c6, BSD 2-Clause; Copyright (c) 2019 Abex).
// See THIRD_PARTY_NOTICES.md ("Farming patch tables and capture guards").

/**
 * Raw varbit value ranges for every supported patch type, as RuneLite decodes
 * them, with Spyglass refinements taken from RuneLite's own object-name
 * comments:
 * <ul>
 *   <li>A "Check-health" object (RuneLite: growing at the last stage) is its own
 *   condition, CHECK_HEALTH.</li>
 *   <li>A harvestable tree-like patch is STANDING ("Chop down") or a STUMP.</li>
 *   <li>GROWN: a fully grown spirit tree ("Travel"), and a scarecrow in a
 *   flower patch: nothing to harvest, nothing growing.</li>
 * </ul>
 * Weeds rows keep RuneLite's weed level; level 0 becomes EMPTY at decode time.
 * A dead herb has no known produce (RuneLite's ANYHERB). Values covered by no
 * row decode as UNKNOWN (for example herb 110-127 and 220, where RuneLite
 * returns null), never as empty.
 */
final class FarmingDecoderTable
{
	private FarmingDecoderTable()
	{
	}

	/** How a row turns a raw value into a stage number. */
	static final int OFFSET = 0;
	static final int REVERSE = 1;
	static final int CONST = 2;
	/** The produce's last growth stage (check-health). */
	static final int LAST = 3;

	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class Row
	{
		final int lo;
		final int hi;
		final PatchCondition condition;
		final FarmingProduce produce;
		final int stageMode;
		final int stageParam;
		final PatchSubState subState;
	}

	/** Resource with every row, in order (read once, on first use; a few hundred short lines). */
	static final String FILE = "/com/spyglassfarming/farming/patch-states.tsv";

	static Row[] rows(FarmingPatchType type)
	{
		Row[] r = Table.ROWS.get(type);
		return r == null ? new Row[0] : r;
	}

	/** Loaded on first use; a missing or malformed file is a build defect and fails loudly. */
	private static final class Table
	{
		static final Map<FarmingPatchType, Row[]> ROWS = load();
	}

	static Map<FarmingPatchType, Row[]> load()
	{
		Map<FarmingPatchType, List<Row>> rows = new EnumMap<>(FarmingPatchType.class);
		DataRows.read(FILE, r -> row(rows, r));
		return arrays(rows);
	}

	static Map<FarmingPatchType, Row[]> parse(BufferedReader reader) throws IOException
	{
		Map<FarmingPatchType, List<Row>> rows = new EnumMap<>(FarmingPatchType.class);
		DataRows.parse(FILE, reader, r -> row(rows, r));
		return arrays(rows);
	}

	private static void row(Map<FarmingPatchType, List<Row>> rows, DataRows.Row r)
	{
		int lo = r.cols(8, 8).i(1);
		int hi = r.i(2);
		if (lo < 0 || hi < lo)
		{
			throw new IllegalArgumentException("bad range " + lo + "-" + hi);
		}
		int mode = mode(r.s(5));
		FarmingProduce produce = r.s(4).isEmpty() ? null : r.e(FarmingProduce.class, 4);
		if (mode == LAST && produce == null)
		{
			throw new IllegalArgumentException("LAST needs a produce");
		}
		rows.computeIfAbsent(r.e(FarmingPatchType.class, 0), k -> new ArrayList<>())
			.add(new Row(lo, hi, r.e(PatchCondition.class, 3), produce, mode, r.i(6), r.e(PatchSubState.class, 7)));
	}

	private static Map<FarmingPatchType, Row[]> arrays(Map<FarmingPatchType, List<Row>> rows)
	{
		Map<FarmingPatchType, Row[]> out = new EnumMap<>(FarmingPatchType.class);
		for (FarmingPatchType type : FarmingPatchType.values())
		{
			List<Row> r = rows.get(type);
			if (r == null || r.isEmpty())
			{
				throw new IllegalStateException(FILE + ": no rows for " + type);
			}
			out.put(type, r.toArray(new Row[0]));
		}
		return out;
	}

	private static int mode(String name)
	{
		switch (name)
		{
			case "OFFSET":
				return OFFSET;
			case "REVERSE":
				return REVERSE;
			case "CONST":
				return CONST;
			case "LAST":
				return LAST;
			default:
				throw new IllegalArgumentException("unknown stage mode " + name);
		}
	}
}
