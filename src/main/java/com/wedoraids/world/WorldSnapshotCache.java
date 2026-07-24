/*
 * Copyright (c) 2026, s59
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wedoraids.world;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.runelite.http.api.worlds.WorldResult;

public final class WorldSnapshotCache
{
	private final Supplier<WorldResult> loadWorlds;
	private final Object worldLifecycleLock = new Object();
	private long worldCacheVersion;
	private volatile WorldResult worldSnapshot;
	private ExecutorService worldLoader;

	public WorldSnapshotCache(Supplier<WorldResult> loadWorlds)
	{
		this.loadWorlds = loadWorlds;
	}

	public void accept(WorldResult snapshot)
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

	public void start()
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

	public void stop()
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
