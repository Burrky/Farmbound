package com.spyglassfarming;

import com.spyglassfarming.navigation.AccountRouter;
import com.spyglassfarming.navigation.LiveRoute;
import com.spyglassfarming.navigation.NavAccess;
import com.spyglassfarming.navigation.Pathfinder;
import com.spyglassfarming.navigation.RoutePlanner;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Background navigation work (canonical §28.11). Two daemon threads: live route searches (at most one at a time, the newest
 * result kept) and account route searches ({@link AccountRouter}, a few seconds each, never in the way of a live search).
 * The client thread only captures immutable inputs (position, an account snapshot, goal tiles) and asks; it never waits.
 *
 * <p>Every request carries a generation. {@link #reset} (run ended, logout, another account or profile, shutdown) moves the
 * generation on and cancels the running search; a result from an older generation is dropped, never published.
 */
@Slf4j
@Singleton
public class NavigationRuntime
{
	private ExecutorService live;
	private ExecutorService routes;
	private volatile Pathfinder pathfinder;
	private volatile LiveRoute latest;
	private final AtomicBoolean busy = new AtomicBoolean();
	private final AtomicLong generation = new AtomicLong();

	public synchronized void start()
	{
		if (live != null)
		{
			return;
		}
		live = Workers.lowPriority("spyglass-navigation");
		routes = Workers.lowPriority("spyglass-routes");
		AccountRouter.useExecutor(routes); // before any data loads: account searches never run on a caller's thread
		live.execute(() -> {
			try
			{
				long t0 = System.nanoTime();
				pathfinder = Pathfinder.bundled();
				AccountRouter.bundled(); // load the route data now (the executor is already set)
				log.debug("Farmbound navigation: world data ready in {} ms", (System.nanoTime() - t0) / 1_000_000L);
			}
			catch (RuntimeException e)
			{
				log.error("Farmbound navigation: the world map or route data failed to load; live routes are off", e);
			}
		});
	}

	public synchronized void stop()
	{
		reset();
		// Closed, not synchronous: a late caller gets the interim answer, never a search on its own (client) thread.
		AccountRouter.useExecutor(r -> {
			throw new RejectedExecutionException("navigation stopped");
		});
		if (live != null)
		{
			live.shutdownNow();
			routes.shutdownNow();
			live = null;
			routes = null;
		}
		busy.set(false);
	}

	/** The newest finished search of the current generation, or null. */
	public LiveRoute latest()
	{
		LiveRoute l = latest;
		return l != null && l.getGeneration() == generation.get() ? l : null;
	}

	/** Forget routes: the running search is cancelled and any result it or an older one produces is dropped. */
	public void reset()
	{
		generation.incrementAndGet();
		latest = null;
	}

	public boolean isBusy()
	{
		return busy.get();
	}

	/** Starts a search unless one is running or the map isn't loaded; returns whether it started. Any thread. */
	public boolean request(String target, int origin, Map<String, RoutePlanner.Goal> goals, NavAccess access, int maxCost)
	{
		ExecutorService ex;
		synchronized (this)
		{
			ex = live;
		}
		Pathfinder pf = pathfinder;
		if (ex == null || pf == null || !busy.compareAndSet(false, true))
		{
			return false;
		}
		long gen = generation.get();
		try
		{
			ex.execute(() -> {
				try
				{
					LiveRoute r = RoutePlanner.plan(pf, target, origin, goals, access, maxCost, RoutePlanner.MAX_NODES,
						() -> generation.get() != gen || Thread.currentThread().isInterrupted(), gen);
					if (generation.get() == gen)
					{
						latest = r; // a stale result (reset since it was asked for) is never published
					}
				}
				catch (RuntimeException e)
				{
					log.debug("Farmbound navigation: route search failed", e);
				}
				finally
				{
					busy.set(false);
				}
			});
			return true;
		}
		catch (RuntimeException e)
		{
			busy.set(false);
			return false;
		}
	}
}
