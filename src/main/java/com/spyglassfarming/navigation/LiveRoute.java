package com.spyglassfarming.navigation;

import lombok.Getter;

/**
 * The result of one live route search from the player's position (canonical §28.11). {@link #getPath()} leads from where the
 * player stood at {@link #getOrigin()} to one of the goals asked for; {@link #getReached()} names which goal group it reached
 * ({@code stop:...} or {@code node:<entrance>}). With no path, {@link #getOutcome()} says why (never shown to the player).
 *
 * <p>Progress along the route is monotonic: it is looked for from where the player last was up to the next transition, and a
 * transition counts as taken only once the player is observed at its arrival ({@link Transition#arrivedAt}); a later part of
 * the route that happens to pass nearby is never matched early. Immutable apart from that progress marker.
 */
public final class LiveRoute
{
	/** What was asked for (the stop's name). */
	@Getter
	private final String target;
	@Getter
	private final int origin;
	@Getter
	private final NavPath path;
	@Getter
	private final NavOutcome outcome;
	/** The goal group the path ends in ("stop:Catherby", "node:ring.cks"), or null. */
	@Getter
	private final String reached;
	@Getter
	private final int explored;
	@Getter
	private final long millis;
	/** The request generation it answers (a newer request or a reset makes it stale). */
	@Getter
	private final long generation;
	private volatile int progressed;

	public LiveRoute(String target, int origin, NavPath path, NavOutcome outcome, String reached, int explored, long millis, long generation)
	{
		this.target = target;
		this.origin = origin;
		this.path = path;
		this.outcome = outcome;
		this.reached = reached;
		this.explored = explored;
		this.millis = millis;
		this.generation = generation;
	}

	/**
	 * Index of the path tile the player is at (within {@code slack} tiles, same plane), searched from the last progress up to
	 * and through the next transition only once its arrival is observed; -1 when off the route.
	 */
	public int progress(int position, int slack)
	{
		if (path == null)
		{
			return -1;
		}
		int from = Math.max(0, progressed - slack);
		int best = -1;
		int bestD = Integer.MAX_VALUE;
		for (int i = from; i < path.size(); i++)
		{
			Transition t = path.via(i);
			if (t != null && i > progressed)
			{
				if (!t.arrivedAt(position))
				{
					break; // not through this transition yet: nothing beyond it can match
				}
				if (best < i)
				{
					best = i; // just arrived through it
					bestD = 0;
				}
			}
			int d = NavPoint.distance(path.tile(i), position);
			if (d <= slack && d <= bestD && i >= best)
			{
				best = i;
				bestD = d;
			}
		}
		if (best >= 0 && best > progressed)
		{
			progressed = best;
		}
		return best >= 0 && best >= progressed - slack ? best : -1;
	}
}
