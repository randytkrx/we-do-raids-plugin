package com.wedoraids;

import static com.wedoraids.LifecyclePanelFixtures.panel;
import static com.wedoraids.LifecyclePluginFixtures.config;
import static com.wedoraids.LifecyclePluginFixtures.field;
import static com.wedoraids.LifecyclePluginFixtures.plugin;
import static com.wedoraids.LifecyclePluginFixtures.setField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import org.junit.Test;

/**
 * A live hosted post belongs to the account that created it: logging out must not strand the
 * Discord post (the host logs back in and closes it), but another account must never inherit it.
 */
public class HostPostIdentityTest
{
	@Test
	public void logoutKeepsLivePostSoTheHostCanStillCloseIt() throws Exception
	{
		withLivePost("Alice", (plugin, hostForm) ->
		{
			final GameStateChanged event = new GameStateChanged();
			event.setGameState(GameState.LOGIN_SCREEN);
			plugin.onGameStateChanged(event);
			drainEdt();

			assertNotNull("logout discarded the live hosted post",
				field(hostForm, "displayedLiveFields"));
			assertEquals("logout cleared the post's owner", "Alice", field(plugin, "hostLiveOwner"));
		});
	}

	@Test
	public void loggingBackInOnTheSameAccountKeepsTheLivePost() throws Exception
	{
		withLivePost("Alice", (plugin, hostForm) ->
		{
			final GameStateChanged event = new GameStateChanged();
			event.setGameState(GameState.LOGIN_SCREEN);
			plugin.onGameStateChanged(event);
			drainEdt();

			setViewerName(plugin, "Alice");
			drainEdt();

			assertNotNull("re-login on the same account discarded the live hosted post",
				field(hostForm, "displayedLiveFields"));
		});
	}

	@Test
	public void loggingInOnADifferentAccountDropsTheLivePost() throws Exception
	{
		withLivePost("Alice", (plugin, hostForm) ->
		{
			setViewerName(plugin, "Bob");
			drainEdt();

			assertNull("a different account inherited the previous account's hosted post",
				field(hostForm, "displayedLiveFields"));
			assertNull("switching accounts left a stale post owner", field(plugin, "hostLiveOwner"));
		});
	}

	private interface LivePostCase
	{
		void run(WeDoRaidsPlugin plugin, HostFormPanel hostForm) throws Exception;
	}

	/** Builds a plugin with a live hosted post owned by {@code owner}, then runs the assertions. */
	private void withLivePost(String owner, LivePostCase body) throws Exception
	{
		final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		final WeDoRaidsPanel[] panel = new WeDoRaidsPanel[1];
		try
		{
			final WeDoRaidsPlugin plugin = plugin(executor, "secret", false, new CountDownLatch(1));
			SwingUtilities.invokeAndWait(() -> panel[0] = panel(config("secret", false)));
			setField(plugin, "panel", panel[0]);
			setField(plugin, "localPlayerName", owner);
			setField(plugin, "hostLiveOwner", owner);

			final HostFormPanel hostForm = (HostFormPanel) field(panel[0], "hostForm");
			final Map<String, String> fields = new LinkedHashMap<>();
			fields.put("raid", "TOB");
			fields.put("spots", "+2");
			setField(hostForm, "lastSubmittedFields", fields);
			SwingUtilities.invokeAndWait(() -> hostForm.enterLivePost("message-id"));

			body.run(plugin, hostForm);
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

	/** The shared fixture's object overload is List-typed, so call the String setter directly. */
	private static void setViewerName(WeDoRaidsPlugin plugin, String viewer) throws Exception
	{
		final java.lang.reflect.Method method =
			WeDoRaidsPlugin.class.getDeclaredMethod("setViewerName", String.class);
		method.setAccessible(true);
		method.invoke(plugin, viewer);
	}

	private static void drainEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}
}
