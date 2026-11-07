package com.spyglassfarming.prep;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * One need's evaluation. Facts only; the consumer's view turns them into
 * player text. Ownership (where it was last seen, and when) is kept apart from
 * carried readiness: an item last seen in the bank is not carried, and its
 * bank observation keeps its age instead of expiring.
 */
public final class NeedResult
{
	/** How a CARRIED need is carried. */
	public enum Via
	{
		INVENTORY,
		WORN,
		RUNE_POUCH,
		/** A worn rune provider (an elemental staff) supplies the rune. */
		STAFF
	}

	@Getter
	private final ItemNeed need;
	@Getter
	private final NeedStatus status;
	/** How many are carried, in the need's units (a large value for an unlimited provider). */
	@Getter
	private final long carried;
	@Getter
	private final long banked;
	/** Bank tab 1-9, 0 = main tab, -1 = unknown or not in the bank. */
	@Getter
	private final int bankTab;
	/** The bank item id to highlight, or -1. */
	@Getter
	private final int bankItemId;
	/** Compact diagnostic text: "carried", "bank · tab 2 (3h)", "not found · bank seen 3h ago". */
	@Getter
	private final String detail;
	/** CARRIED: how (inventory, worn, rune pouch, staff); otherwise null. */
	@Getter
	private Via via;
	/** When the bank used for this result was observed, or -1 when it never was. */
	@Getter
	private long bankSeenAt = -1;
	@Getter
	private long elsewhereSeenAt = -1;
	/** ELSEWHERE: the other storages that had it ("group storage", "seed vault"). */
	@Getter
	private List<String> places = Collections.emptyList();
	/** The item to picture: the variant carried, else the one last seen stored, else the first acceptable one. */
	@Getter
	private int displayItemId;
	/** Items to take out of the bank to cover what is still needed (IN_BANK only; otherwise 0). */
	@Getter
	private long withdrawItems;
	/** NOT_FOUND: how many units were found in total across the observed places. */
	@Getter
	private long found;
	/** WORN_ONLY needs: carried in the inventory but not worn (its effect needs it equipped). */
	@Getter
	private boolean carriedNotWorn;

	NeedResult(ItemNeed need, NeedStatus status, long carried, long banked, int bankTab, int bankItemId, String detail)
	{
		this.need = need;
		this.status = status;
		this.carried = carried;
		this.banked = banked;
		this.bankTab = bankTab;
		this.bankItemId = bankItemId;
		this.detail = detail;
		this.displayItemId = need.getAcceptableItemIds()[0];
	}

	NeedResult via(Via v)
	{
		this.via = v;
		return this;
	}

	NeedResult bankSeenAt(long t)
	{
		this.bankSeenAt = t;
		return this;
	}

	NeedResult elsewhere(List<String> where, long seenAt)
	{
		this.places = Collections.unmodifiableList(where);
		this.elsewhereSeenAt = seenAt;
		return this;
	}

	NeedResult display(int itemId)
	{
		if (itemId > 0)
		{
			this.displayItemId = itemId;
		}
		return this;
	}

	NeedResult withdraw(long items)
	{
		this.withdrawItems = items;
		return this;
	}

	NeedResult found(long n)
	{
		this.found = n;
		return this;
	}

	/** Covered: carried, or held in a store reachable where it is used ({@link NeedStatus#REACHABLE}). */
	public boolean isSatisfied()
	{
		return status == NeedStatus.CARRIED && !carriedNotWorn || status == NeedStatus.REACHABLE;
	}

	NeedResult carriedNotWorn(boolean b)
	{
		this.carriedNotWorn = b;
		return this;
	}
}
