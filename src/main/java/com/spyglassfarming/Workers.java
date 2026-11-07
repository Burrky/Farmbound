package com.spyglassfarming;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Spyglass's background workers: each is one named daemon thread running its tasks in order (a daemon never keeps the
 * client alive), and each shuts down gracefully.
 */
public final class Workers
{
	private Workers()
	{
	}

	/** A worker on one named daemon thread. */
	public static ExecutorService serial(String name)
	{
		return worker(name, false);
	}

	/** {@link #serial} at the lowest thread priority (planning work that must never compete with the client). */
	public static ExecutorService lowPriority(String name)
	{
		return worker(name, true);
	}

	private static ExecutorService worker(String name, boolean low)
	{
		return Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, name);
			t.setDaemon(true);
			if (low)
			{
				t.setPriority(Thread.MIN_PRIORITY);
			}
			return t;
		});
	}

	/** Hands work to {@code executor}; work handed over after it shut down (an in-flight tick) is dropped with {@code note}, never thrown at the caller. */
	public static Executor dropAfterShutdown(ExecutorService executor, Logger log, String note)
	{
		return task ->
		{
			try
			{
				executor.execute(task);
			}
			catch (RejectedExecutionException e)
			{
				log.debug(note);
			}
		};
	}

	/**
	 * Stops taking work and gives queued work up to two seconds to finish. Work still running after that keeps going in the
	 * background rather than being interrupted (never a half-written file); {@code name} names the worker in the log.
	 */
	public static void drain(ExecutorService executor, String name, Logger log)
	{
		executor.shutdown();
		try
		{
			if (!executor.awaitTermination(2, TimeUnit.SECONDS))
			{
				log.warn("{} did not drain within the graceful window; it will keep draining in the background rather than being forcibly interrupted", name);
			}
		}
		catch (InterruptedException e)
		{
			log.warn("Interrupted while awaiting graceful drain of the {}; leaving it to keep draining in the background", name);
		}
	}
}
