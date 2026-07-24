package com.wedoraids;

import static com.wedoraids.LifecycleExecutorFixtures.blockingResponse;
import static com.wedoraids.LifecyclePluginFixtures.config;
import static com.wedoraids.LifecyclePluginFixtures.field;
import static com.wedoraids.LifecyclePluginFixtures.invoke;
import static com.wedoraids.LifecyclePanelFixtures.panel;
import static com.wedoraids.LifecyclePluginFixtures.plugin;
import static com.wedoraids.LifecyclePluginFixtures.setField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import okhttp3.OkHttpClient;
import org.junit.Test;

public class IdentityLifecycleTest
{
	@Test
	public void loginScreenResetsIdentityFeedStateAndCancelsPolling()
		throws Exception
	{
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			WeDoRaidsPlugin plugin = plugin(executor, "secret", false, new CountDownLatch(1));
			setField(plugin, "localPlayerName", "Alice");
			setField(plugin, "localBanned", true);
			setField(plugin, "localVerified", true);
			setField(plugin, "coxKc", 100);
			setField(plugin, "tobKc", 200);
			setField(plugin, "toaKc", 300);
			@SuppressWarnings("unchecked")
			Set<String> notified = (Set<String>) field(plugin, "notifiedKeys");
			notified.add("seen");
			@SuppressWarnings("unchecked")
			Set<String> tobHosts = (Set<String>) field(plugin, "activeTobHosts");
			tobHosts.add("alice");
			@SuppressWarnings("unchecked")
			Set<String> toaHosts = (Set<String>) field(plugin, "activeToaHosts");
			toaHosts.add("alice");
			ScheduledFuture<?> task = executor.schedule(() ->
			{
			}, 1, TimeUnit.MINUTES);
			setField(plugin, "remoteFeedTask", task);

			GameStateChanged event = new GameStateChanged();
			event.setGameState(GameState.LOGIN_SCREEN);
			plugin.onGameStateChanged(event);

			assertEquals(null, field(plugin, "localPlayerName"));
			assertFalse((Boolean) field(plugin, "localBanned"));
			assertFalse((Boolean) field(plugin, "localVerified"));
			assertEquals(-1, field(plugin, "coxKc"));
			assertEquals(-1, field(plugin, "tobKc"));
			assertEquals(-1, field(plugin, "toaKc"));
			assertTrue(notified.isEmpty());
			assertTrue(tobHosts.isEmpty());
			assertTrue(toaHosts.isEmpty());
			assertTrue(task.isCancelled());
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void identityResetClearsHostedRaidAndInvalidatesLiveOperation()
		throws Exception
	{
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		final WeDoRaidsPanel[] panel = new WeDoRaidsPanel[1];
		try
		{
			WeDoRaidsPlugin plugin = plugin(executor, "secret", false, new CountDownLatch(1));
			SwingUtilities.invokeAndWait(() -> panel[0] = panel(config("secret", false)));
			setField(plugin, "panel", panel[0]);
			HostFormPanel hostForm = (HostFormPanel) field(panel[0], "hostForm");
			Map<String, String> fields = new LinkedHashMap<>();
			fields.put("raid", "TOB");
			fields.put("spots", "+2");
			setField(hostForm, "lastSubmittedFields", fields);
			SwingUtilities.invokeAndWait(() -> hostForm.enterLivePost("message-id"));
			HostLiveState liveState = (HostLiveState) field(hostForm, "liveState");
			assertTrue(liveState.beginOperation() > 0);

			invoke(plugin, "resetIdentityFeedState");
			SwingUtilities.invokeAndWait(() ->
			{
			});

			assertNull("identity reset retained the live message", field(hostForm, "displayedLiveFields"));
			assertNull("identity reset retained confirmed hosted-raid state", liveState.confirmedFieldsSnapshot());
			assertTrue("identity reset left a host operation in flight", liveState.canStartOperation());
		}
		finally
		{
			if (panel[0] != null)
			{
				SwingUtilities.invokeAndWait(panel[0]::shutdown);
			}
			executor.shutdownNow();
		}
	}

	@Test
	public void identityResetDuringKcParsePreventsStaleCommit()
		throws Exception
	{
		CountDownLatch parseStarted = new CountDownLatch(1);
		CountDownLatch releaseParse = new CountDownLatch(1);
		CountDownLatch callFinished = new CountDownLatch(1);
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> blockingResponse(chain,
			"{\"verified\":true,\"cox\":101,\"tob\":202,\"toa\":303}", parseStarted, releaseParse,
			callFinished))
			.build();
		WeDoRaidsPlugin plugin = new WeDoRaidsPlugin();
		setField(plugin, "config", config("secret", false));
		setField(plugin, "localPlayerName", "Alice");
		setField(plugin, "okHttpClient", client);
		setField(plugin, "gson", new Gson());
		try
		{
			invoke(plugin, "fetchKc");
			assertTrue(parseStarted.await(5, TimeUnit.SECONDS));
			invoke(plugin, "resetIdentityFeedState");
		}
		finally
		{
			releaseParse.countDown();
		}
		assertTrue(callFinished.await(5, TimeUnit.SECONDS));
		assertEquals(-1, field(plugin, "coxKc"));
		assertEquals(-1, field(plugin, "tobKc"));
		assertEquals(-1, field(plugin, "toaKc"));
	}

	@Test
	public void panelStartsWithPluginUnverifiedDefault()
		throws Exception
	{
		final WeDoRaidsPanel[] panel = new WeDoRaidsPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = panel(config("secret", false)));
		try
		{
			WeDoRaidsPlugin plugin = new WeDoRaidsPlugin();
			assertFalse((Boolean) field(plugin, "localVerified"));
			assertFalse("the panel started verified before the plugin verified the player",
				(Boolean) field(panel[0], "verified"));
		}
		finally
		{
			SwingUtilities.invokeAndWait(panel[0]::shutdown);
		}
	}
}
