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

import static com.wedoraids.LifecyclePluginFixtures.invoke;
import static com.wedoraids.LifecyclePluginFixtures.setField;
import static com.wedoraids.LifecycleWorldFixtures.world;
import static com.wedoraids.LifecycleWorldFixtures.worlds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.wedoraids.LifecycleWorldFixtures.BlockingWorldPlugin;
import com.wedoraids.LifecycleWorldFixtures.CompletedWorldPlugin;
import com.wedoraids.LifecycleWorldFixtures.RecordingClientThread;
import com.wedoraids.LifecycleWorldFixtures.RestartingWorldPlugin;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.client.events.WorldsFetch;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;

public class WorldCacheLifecycleTest
{
	@Test
	public void worldCacheLoadsOnDedicatedDaemonAndWorldsFetchDrivesEdtValidation()
		throws Exception
	{
		BlockingWorldPlugin plugin = new BlockingWorldPlugin();
		AtomicReference<Thread> edtThread = new AtomicReference<>();
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				edtThread.set(Thread.currentThread());
				plugin.startWorldCache();
			});
			assertTrue(plugin.loadStarted.await(5, TimeUnit.SECONDS));
			assertTrue(plugin.loaderThread.get().isDaemon());
			assertFalse(edtThread.get() == plugin.loaderThread.get());

			WorldResult normalWorld = worlds(world(301, EnumSet.noneOf(WorldType.class)));
			SwingUtilities.invokeAndWait(() -> plugin.onWorldsFetch(new WorldsFetch(normalWorld)));
			AtomicReference<String> reason = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> reason.set(plugin.worldBlockReason(301)));
			assertNull(reason.get());
		}
		finally
		{
			plugin.stopWorldCache();
			plugin.releaseLoad.countDown();
		}
	}

	@Test
	public void completedWorldBootstrapTerminatesExecutorAndKeepsWorldsFetchActive()
		throws Exception
	{
		CompletedWorldPlugin plugin = new CompletedWorldPlugin();
		try
		{
			plugin.startWorldCache();

			assertTrue(plugin.loadCompleted.await(5, TimeUnit.SECONDS));
			Thread loader = plugin.loaderThread.get();
			assertTrue("completed world bootstrap did not expose its loader thread", loader != null);
			loader.join(TimeUnit.SECONDS.toMillis(5));
			assertTrue("completed world bootstrap left its executor alive",
			!loader.isAlive());
			assertNull(plugin.worldBlockReason(301));

			plugin.onWorldsFetch(new WorldsFetch(worlds(world(301, EnumSet.of(WorldType.PVP)))));
			assertEquals("a PvP world", plugin.worldBlockReason(301));
		}
		finally
		{
			plugin.stopWorldCache();
		}
	}

	@Test
	public void staleWorldLoaderCannotPublishAfterShutdownAndRestart()
		throws Exception
	{
		RestartingWorldPlugin plugin = new RestartingWorldPlugin();
		try
		{
			plugin.startWorldCache();
			assertTrue(plugin.firstLoadStarted.await(5, TimeUnit.SECONDS));
			Thread staleLoader = plugin.firstLoaderThread.get();

			plugin.stopWorldCache();
			plugin.startWorldCache();
			assertTrue(plugin.secondLoadStarted.await(5, TimeUnit.SECONDS));
			plugin.onWorldsFetch(new WorldsFetch(worlds(world(301, EnumSet.noneOf(WorldType.class)))));
			assertNull(plugin.worldBlockReason(301));

			plugin.releaseFirstLoad.countDown();
			staleLoader.join(TimeUnit.SECONDS.toMillis(5));
			assertFalse("stale world loader did not terminate", staleLoader.isAlive());
			assertNull("stale loader replaced the restarted cache", plugin.worldBlockReason(301));
		}
		finally
		{
			plugin.stopWorldCache();
			plugin.releaseFirstLoad.countDown();
			plugin.releaseSecondLoad.countDown();
		}
	}

	@Test
	public void panelHopUsesCachedWorldSnapshotWithoutServiceLookup()
		throws Exception
	{
		BlockingWorldPlugin plugin = new BlockingWorldPlugin();
		RecordingClientThread clientThread = new RecordingClientThread();
		setField(plugin, "clientThread", clientThread);
		try
		{
			plugin.startWorldCache();
			assertTrue(plugin.loadStarted.await(5, TimeUnit.SECONDS));
			plugin.onWorldsFetch(new WorldsFetch(worlds(world(301, EnumSet.noneOf(WorldType.class)))));

			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					invoke(plugin, "hopTo", 301);
				}
				catch (Exception e)
				{
					throw new AssertionError(e);
				}
			});

			assertEquals(1, clientThread.invokeCount.get());
		}
		finally
		{
			plugin.stopWorldCache();
			plugin.releaseLoad.countDown();
		}
	}
}
