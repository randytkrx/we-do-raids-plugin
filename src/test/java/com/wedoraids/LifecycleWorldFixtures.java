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
package com.wedoraids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

final class LifecycleWorldFixtures
{
	private LifecycleWorldFixtures()
	{
	}

	static World world(int id, EnumSet<WorldType> types)
	{
		return World.builder().id(id).types(types).address("oldschool" + id + ".runescape.com")
			.activity("").location(0).players(0).build();
	}

	static WorldResult worlds(World... values)
	{
		WorldResult result = new WorldResult();
		result.setWorlds(new ArrayList<>(Arrays.asList(values)));
		return result;
	}

	static void awaitIgnoringInterrupt(CountDownLatch latch)
	{
		boolean interrupted = false;
		while (true)
		{
			try
			{
				latch.await();
				break;
			}
			catch (InterruptedException ignored)
			{
				interrupted = true;
			}
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}
	static class BlockingWorldPlugin extends WeDoRaidsPlugin
	{
		final CountDownLatch loadStarted = new CountDownLatch(1);
		final CountDownLatch releaseLoad = new CountDownLatch(1);
		final AtomicReference<Thread> loaderThread = new AtomicReference<>();

		@Override
		WorldResult loadWorlds()
		{
			loaderThread.set(Thread.currentThread());
			loadStarted.countDown();
			awaitIgnoringInterrupt(releaseLoad);
			return null;
		}
	}

	static final class CompletedWorldPlugin extends WeDoRaidsPlugin
	{
		final CountDownLatch loadCompleted = new CountDownLatch(1);
		final AtomicReference<Thread> loaderThread = new AtomicReference<>();

		@Override
		WorldResult loadWorlds()
		{
			loaderThread.set(Thread.currentThread());
			loadCompleted.countDown();
			return worlds(world(301, EnumSet.noneOf(WorldType.class)));
		}
	}

	static final class RestartingWorldPlugin extends WeDoRaidsPlugin
	{
		private final AtomicInteger loadCount = new AtomicInteger();
		final CountDownLatch firstLoadStarted = new CountDownLatch(1);
		final CountDownLatch secondLoadStarted = new CountDownLatch(1);
		final CountDownLatch releaseFirstLoad = new CountDownLatch(1);
		final CountDownLatch releaseSecondLoad = new CountDownLatch(1);
		final AtomicReference<Thread> firstLoaderThread = new AtomicReference<>();

		@Override
		WorldResult loadWorlds()
		{
			if (loadCount.incrementAndGet() == 1)
			{
				firstLoaderThread.set(Thread.currentThread());
				firstLoadStarted.countDown();
				awaitIgnoringInterrupt(releaseFirstLoad);
				return worlds(world(301, EnumSet.of(WorldType.PVP)));
			}
			secondLoadStarted.countDown();
			awaitIgnoringInterrupt(releaseSecondLoad);
			return null;
		}
	}

	static final class RecordingClientThread extends ClientThread
	{
		final AtomicInteger invokeCount = new AtomicInteger();

		@Override
		public void invoke(Runnable runnable)
		{
			invokeCount.incrementAndGet();
		}
	}
}
