package com.wedoraids;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.runelite.http.api.worlds.WorldResult;

final class WorldSnapshotCache
{
	private final Supplier<WorldResult> loadWorlds;
	private final Object worldLifecycleLock = new Object();
	private long worldCacheVersion;
	private volatile WorldResult worldSnapshot;
	private ExecutorService worldLoader;

	WorldSnapshotCache(Supplier<WorldResult> loadWorlds)
	{
		this.loadWorlds = loadWorlds;
	}

	void accept(WorldResult snapshot)
	{
		synchronized (worldLifecycleLock)
		{
			if (worldLoader != null)
			{
				worldSnapshot = snapshot;
				worldCacheVersion++;
			}
		}
	}

	WorldResult snapshot()
	{
		return worldSnapshot;
	}

	void start()
	{
		final ExecutorService replacement = Executors.newSingleThreadExecutor(runnable ->
		{
			final Thread thread = new Thread(runnable, "we-do-raids-world-loader");
			thread.setDaemon(true);
			return thread;
		});
		final ExecutorService previous;
		final long revision;
		synchronized (worldLifecycleLock)
		{
			previous = worldLoader;
			worldSnapshot = null;
			revision = ++worldCacheVersion;
			worldLoader = replacement;
			replacement.execute(() -> publishLoadedWorlds(replacement, revision));
		}
		if (previous != null)
		{
			previous.shutdownNow();
		}
	}

	void stop()
	{
		final ExecutorService loader;
		synchronized (worldLifecycleLock)
		{
			worldCacheVersion++;
			worldSnapshot = null;
			loader = worldLoader;
			worldLoader = null;
		}
		if (loader != null)
		{
			loader.shutdownNow();
		}
	}

	private void publishLoadedWorlds(ExecutorService loader, long revision)
	{
		try
		{
			final WorldResult loaded = loadWorlds.get();
			synchronized (worldLifecycleLock)
			{
				if (loaded != null && worldLoader == loader && worldCacheVersion == revision)
				{
					worldSnapshot = loaded;
				}
			}
		}
		finally
		{
			loader.shutdown();
		}
	}
}
