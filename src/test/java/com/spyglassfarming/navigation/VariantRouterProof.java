package com.spyglassfarming.navigation;

import com.spyglassfarming.account.AccountCapability;
import com.spyglassfarming.account.AccountFixtures;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import org.junit.Test;

/**
 * Proof run (not a regression test): for every Farming stop and every catalogue arrival, does the cheapest precomputed walk
 * variant this account can use give the same answer as the account search? Run by hand; prints the comparison.
 */
public class VariantRouterProof
{
	static List<Object[]> accounts()
	{
		List<Object[]> out = new ArrayList<>();
		out.add(new Object[]{"maxed", AccountFixtures.maxed().build()});
		out.add(new Object[]{"fresh (level 1, no quests)", AccountFixtures.base(1).build()});
		AccountCapability.Builder mid = AccountFixtures.base(50);
		int i = 0;
		for (Quest q : Quest.values())
		{
			mid.quest(q, i++ % 2 == 0 ? QuestState.FINISHED : QuestState.NOT_STARTED);
		}
		out.add(new Object[]{"mid (level 50, half the quests)", mid.build()});
		AccountCapability.Builder low = AccountFixtures.base(30);
		int j = 0;
		for (Quest q : Quest.values())
		{
			low.quest(q, j++ % 5 == 0 ? QuestState.FINISHED : QuestState.NOT_STARTED);
		}
		out.add(new Object[]{"low (level 30, a fifth of the quests)", low.build()});
		return out;
	}

	@Test
	public void compare()
	{
		if (System.getenv("VARIANT_PROOF") == null)
		{
			return;
		}
		TestRoutes.install();
		RouteTable table = RouteTable.bundled();
		AccountRouter router = new AccountRouter(Pathfinder.bundled(), table);
		for (Object[] a : accounts())
		{
			AccountCapability cap = (AccountCapability) a[1];
			AccountRouter.Key key = router.planning(cap);
			int same = 0;
			int longer = 0;
			int lost = 0;
			int gained = 0;
			int pairs = 0;
			long extra = 0;
			List<String> examples = new ArrayList<>();
			for (String to : table.destinations())
			{
				if (!to.startsWith("stop:"))
				{
					continue;
				}
				for (String from : table.startsFor(to))
				{
					pairs++;
					AccountRouter.Answer exact = router.walk(to, from, key);
					RouteTable.Walk variant = null;
					for (RouteTable.Walk w : table.variants(to, from))
					{
						if (allowed(w, key))
						{
							variant = w;
							break;
						}
					}
					boolean e = exact.getOutcome() == NavOutcome.FOUND;
					if (e && variant == null)
					{
						lost++;
						if (examples.size() < 8)
						{
							examples.add("LOST " + to + " <- " + from + " exact " + exact.getWalk().getCost());
						}
					}
					else if (!e && variant != null)
					{
						gained++;
					}
					else if (e)
					{
						int d = variant.getCost() - exact.getWalk().getCost();
						if (d == 0)
						{
							same++;
						}
						else
						{
							longer++;
							extra += d;
							if (examples.size() < 8)
							{
								examples.add("LONGER " + to + " <- " + from + " " + exact.getWalk().getCost() + " -> " + variant.getCost());
							}
						}
					}
					else
					{
						same++;
					}
				}
			}
			System.out.println("PROOF " + a[0] + ": pairs " + pairs + " same " + same + " longer " + longer + " (+" + extra + " tiles) lost " + lost
				+ " gained " + gained);
			examples.forEach(x -> System.out.println("  " + x));
		}
	}

	private static boolean allowed(RouteTable.Walk w, AccountRouter.Key key)
	{
		for (Transition t : w.getTransitions())
		{
			if (!key.access().allowsTransition(t))
			{
				return false;
			}
		}
		return true;
	}
}
