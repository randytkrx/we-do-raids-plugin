package com.wedoraids;

import static com.wedoraids.LifecycleTestSupport.field;
import static com.wedoraids.LifecycleTestSupport.invoke;
import static com.wedoraids.LifecycleTestSupport.setField;
import static com.wedoraids.LifecycleTestSupport.world;
import static com.wedoraids.LifecycleTestSupport.worlds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.wedoraids.LifecycleTestSupport.BlockingWorldPlugin;
import com.wedoraids.LifecycleTestSupport.CompletedWorldPlugin;
import com.wedoraids.LifecycleTestSupport.RecordingClientThread;
import com.wedoraids.LifecycleTestSupport.RestartingWorldPlugin;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
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
			ExecutorService loader = (ExecutorService) field(plugin, "worldLoader");

			assertTrue(plugin.loadCompleted.await(5, TimeUnit.SECONDS));
			assertTrue("completed world bootstrap left its executor alive",
				loader.awaitTermination(5, TimeUnit.SECONDS));
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
