package com.wedoraids;

import static com.wedoraids.LifecycleTestSupport.await;
import static com.wedoraids.LifecycleTestSupport.awaitIgnoringInterrupt;
import static com.wedoraids.LifecycleTestSupport.config;
import static com.wedoraids.LifecycleTestSupport.declaredMethod;
import static com.wedoraids.LifecycleTestSupport.field;
import static com.wedoraids.LifecycleTestSupport.invoke;
import static com.wedoraids.LifecycleTestSupport.invokeBridgeStatus;
import static com.wedoraids.LifecycleTestSupport.mutableConfig;
import static com.wedoraids.LifecycleTestSupport.plugin;
import static com.wedoraids.LifecycleTestSupport.setField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.wedoraids.LifecycleTestSupport.CapturingScheduledExecutor;
import com.wedoraids.LifecycleTestSupport.ImmediateScheduledExecutor;
import com.wedoraids.LifecycleTestSupport.RecordingBridgePanel;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.client.events.ConfigChanged;
import okhttp3.OkHttpClient;
import org.junit.Test;

public class RemoteFeedSchedulingLifecycleTest
{
	@Test
	public void demoModeWithBlankKeyDoesNotScheduleFeedRequest()
		throws Exception
	{
		CapturingScheduledExecutor executor = new CapturingScheduledExecutor();
		try
		{
			WeDoRaidsPlugin plugin = plugin(executor, "", true, new CountDownLatch(1));
			invoke(plugin, "rescheduleRemoteFeed");

			assertEquals(0, executor.taskCount());
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void rescheduleDoesNotClearSeenNotificationKeys()
		throws Exception
	{
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			WeDoRaidsPlugin plugin = plugin(executor, "secret", false, new CountDownLatch(1));
			@SuppressWarnings("unchecked")
			Set<String> notified = (Set<String>) field(plugin, "notifiedKeys");
			notified.add("already-seen");

			invoke(plugin, "rescheduleRemoteFeed");

			assertTrue(notified.contains("already-seen"));
			// Exact re-notification counting also needs seams for the concrete Notifier and
			// the private poll-to-acceptEntries path; preserving the key is all we can isolate.
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void cancelledScheduledPollCannotStartWithObsoleteEpoch()
		throws Exception
	{
		CapturingScheduledExecutor executor = new CapturingScheduledExecutor();
		try
		{
			CountDownLatch polled = new CountDownLatch(1);
			WeDoRaidsPlugin plugin = plugin(executor, "secret", false, polled);
			setField(plugin, "localPlayerName", "Alice");

			invoke(plugin, "rescheduleRemoteFeed");
			invoke(plugin, "rescheduleRemoteFeed");

			assertEquals(2, executor.taskCount());
			assertTrue(executor.future(0).isCancelled());
			executor.runTask(0);
			assertFalse("a cancelled schedule started a poll with its obsolete epoch",
				polled.await(0, TimeUnit.MILLISECONDS));
			executor.runTask(1);
			assertTrue("the current schedule did not start with its issued epoch",
				polled.await(5, TimeUnit.SECONDS));
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void pollStatusPublishesConnectingBeforeOnlineOnEdt()
		throws Exception
	{
		ImmediateScheduledExecutor executor = new ImmediateScheduledExecutor();
		final RecordingBridgePanel[] panels = new RecordingBridgePanel[1];
		try
		{
			WeDoRaidsPlugin plugin = new WeDoRaidsPlugin();
			setField(plugin, "config", config("secret", false));
			setField(plugin, "executor", executor);
			setField(plugin, "localPlayerName", "Alice");
			SwingUtilities.invokeAndWait(() -> panels[0] = new RecordingBridgePanel(config("secret", false)));
			setField(plugin, "panel", panels[0]);
			BridgeStatusListener bridgeStatusListener = (lifecycle, online) ->
			{
				try
				{
					Method method = declaredMethod(WeDoRaidsPlugin.class, "setBridgeOnline", long.class,
						boolean.class);
					method.setAccessible(true);
					method.invoke(plugin, lifecycle, online);
				}
				catch (Exception e)
				{
					throw new AssertionError(e);
				}
			};
			RemoteFeedPoller poller = new RemoteFeedPoller(new OkHttpClient(), new Gson(), values -> { },
				values -> { }, (lifecycle, online) ->
					bridgeStatusListener.onBridgeStatus(lifecycle, online), value -> { })
			{
				@Override
				void poll(String url, String key, String viewer, long epoch, long lifecycle)
				{
					bridgeStatusListener.onBridgeStatus(lifecycle, true);
				}
			};
			setField(plugin, "remoteFeedPoller", poller);

			invoke(plugin, "rescheduleRemoteFeed");
			SwingUtilities.invokeAndWait(() -> { });

			assertEquals(Arrays.asList(BridgeStatus.CONNECTING, BridgeStatus.ONLINE), panels[0].statuses);
		}
		finally
		{
			if (panels[0] != null)
			{
				SwingUtilities.invokeAndWait(panels[0]::shutdown);
			}
			executor.shutdownNow();
		}
	}

	@Test
	public void staleFeedLifecycleStatusIsDroppedAndCurrentStatusIsAccepted()
		throws Exception
	{
		WeDoRaidsPlugin plugin = new WeDoRaidsPlugin();
		final RecordingBridgePanel[] panels = new RecordingBridgePanel[1];
		SwingUtilities.invokeAndWait(() -> panels[0] = new RecordingBridgePanel(config("secret", false)));
		RecordingBridgePanel panel = panels[0];
		CountDownLatch edtBlocked = new CountDownLatch(1);
		CountDownLatch releaseEdt = new CountDownLatch(1);
		try
		{
			setField(plugin, "panel", panel);
			setField(plugin, "feedLifecycleGeneration", 7L);
			SwingUtilities.invokeLater(() ->
			{
				edtBlocked.countDown();
				awaitIgnoringInterrupt(releaseEdt);
			});
			await(edtBlocked);
			invokeBridgeStatus(plugin, BridgeStatus.ONLINE, 7L);
			setField(plugin, "feedLifecycleGeneration", 8L);
			releaseEdt.countDown();
			SwingUtilities.invokeAndWait(() -> { });

			assertTrue("a stale lifecycle status reached the panel", panel.statuses.isEmpty());

			invokeBridgeStatus(plugin, BridgeStatus.OFFLINE, 8L);
			SwingUtilities.invokeAndWait(() -> { });

			assertEquals(Arrays.asList(BridgeStatus.OFFLINE), panel.statuses);
		}
		finally
		{
			releaseEdt.countDown();
			SwingUtilities.invokeAndWait(panel::shutdown);
		}
	}

	@Test
	public void keyRemovalCancelsRescheduleWhoseTaskInstallationWasAlreadyInFlight()
		throws Exception
	{
		assertTransitionCancelsInFlightSchedule(false);
	}

	@Test
	public void demoEnableCancelsRescheduleWhoseTaskInstallationWasAlreadyInFlight()
		throws Exception
	{
		assertTransitionCancelsInFlightSchedule(true);
	}

	@Test
	public void concurrentReschedulesLeaveAtMostOneUncancelledFuture()
		throws Exception
	{
		CapturingScheduledExecutor executor = new CapturingScheduledExecutor(2);
		ExecutorService callers = Executors.newFixedThreadPool(2);
		Future<?> first = null;
		Future<?> second = null;
		try
		{
			WeDoRaidsPlugin plugin = plugin(executor, "secret", false, new CountDownLatch(1));
			setField(plugin, "localPlayerName", "Alice");

			first = callers.submit(() ->
			{
				invoke(plugin, "rescheduleRemoteFeed");
				return null;
			});
			second = callers.submit(() ->
			{
				invoke(plugin, "rescheduleRemoteFeed");
				return null;
			});
			executor.awaitBlockedSchedules();
			executor.releaseSchedules();
			first.get(5, TimeUnit.SECONDS);
			second.get(5, TimeUnit.SECONDS);

			assertEquals("concurrent reschedules orphaned more than one live scheduled future",
				1, executor.uncancelledFutureCount());
		}
		finally
		{
			executor.releaseSchedules();
			if (first != null)
			{
				first.cancel(true);
			}
			if (second != null)
			{
				second.cancel(true);
			}
			callers.shutdownNow();
			executor.cancelAll();
			executor.shutdownNow();
		}
	}

	private static void assertTransitionCancelsInFlightSchedule(boolean enableDemo)
		throws Exception
	{
		CapturingScheduledExecutor executor = new CapturingScheduledExecutor(1);
		ExecutorService caller = Executors.newSingleThreadExecutor();
		AtomicReference<String> key = new AtomicReference<>("secret");
		AtomicBoolean demo = new AtomicBoolean();
		CountDownLatch polled = new CountDownLatch(1);
		Future<?> reschedule = null;
		try
		{
			WeDoRaidsPlugin plugin = plugin(executor, mutableConfig(key, demo), polled);
			setField(plugin, "localPlayerName", "Alice");
			reschedule = caller.submit(() ->
			{
				invoke(plugin, "rescheduleRemoteFeed");
				return null;
			});
			executor.awaitBlockedSchedules();

			if (enableDemo)
			{
				demo.set(true);
			}
			else
			{
				key.set("");
			}
			ConfigChanged event = new ConfigChanged();
			event.setGroup("wedoraids");
			event.setKey(enableDemo ? "demoData" : "remoteFeedKey");
			plugin.onConfigChanged(event);
			executor.releaseSchedules();
			reschedule.get(5, TimeUnit.SECONDS);

			executor.runTask(0);
			assertFalse("a pre-transition scheduled runnable polled after the transition",
				polled.await(0, TimeUnit.MILLISECONDS));
			assertEquals("a pre-transition reschedule installed a live future after cancellation",
				0, executor.uncancelledFutureCount());
			assertNull("a pre-transition reschedule republished its future after cancellation",
				field(plugin, "remoteFeedTask"));
		}
		finally
		{
			executor.releaseSchedules();
			if (reschedule != null)
			{
				reschedule.cancel(true);
			}
			caller.shutdownNow();
			executor.cancelAll();
			executor.shutdownNow();
		}
	}
}
