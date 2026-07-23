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

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.WorldsFetch;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.raids.Raid;
import net.runelite.client.plugins.raids.RaidRoom;
import net.runelite.client.plugins.raids.RoomType;
import net.runelite.client.plugins.raids.events.RaidReset;
import net.runelite.client.plugins.raids.events.RaidScouted;
import net.runelite.client.plugins.raids.solver.Room;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.Notifier;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "We Do Raids",
	description = "The official We Do Raids Discord plugin — find, join and host WDR raid recruitment (ToB/CoX/ToA) in-game",
	tags = {"wdr", "raids", "tob", "cox", "toa", "lfg", "lfm", "recruit", "party", "team"}
)
public class WeDoRaidsPlugin extends Plugin
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	/** Default We Do Raids bridge; overridable via the advanced config for testing. */
	private static final String DEFAULT_BRIDGE_URL = "https://wdr.timecapsule.ink/recruits";
	/** Fixed feed refresh interval (seconds). Locked — not user-configurable. */
	private static final int POLL_SECONDS = 10;

	@Inject
	private WeDoRaidsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Notifier notifier;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private WorldService worldService;
	private final WorldSnapshotCache worldSnapshotCache = new WorldSnapshotCache(this::loadWorlds);

	@Inject
	private net.runelite.client.ui.overlay.OverlayManager overlayManager;

	@Inject
	private net.runelite.client.party.PartyService partyService;

	private WeDoRaidsPanel panel;
	private NavigationButton navButton;
	private RaidBoardOverlay boardOverlay;
	private RemoteFeedPoller remoteFeedPoller;
	private ScheduledFuture<?> remoteFeedTask;
	/** Owns feed task replacement; it may nest identityLock, never the reverse. */
	private final Object feedLifecycleLock = new Object();
	private long feedLifecycleGeneration;
	/** Keys of calls we've already notified about, so we don't re-ping every poll. */
	private final java.util.Set<String> notifiedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** Normalized host names with an active callout, for the ToB/ToA party-board highlight. */
	private final java.util.Set<String> activeTobHosts = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<String> activeToaHosts = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Local player's RSN, or null when logged out; identifies bridge requests. */
	private volatile String localPlayerName;
	/** The world the player is currently on, or 0 when logged out. */
	private volatile int currentWorld;
	/** Latest scouted CoX layout (non-CM only; the raids plugin never scouts CM). */
	private volatile String currentCoxLayout;
	/** Whether the WDR bridge reported the local player as banned. */
	private volatile boolean localBanned;
	private volatile boolean localVerified;
	private final Object identityLock = new Object();
	private final java.util.concurrent.atomic.AtomicLong identityGeneration =
		new java.util.concurrent.atomic.AtomicLong();
	/** Sample entries shown while demo mode is enabled.
	 *  Copy-on-write: mutated on the config thread, iterated on the poller's thread. */
	private final java.util.List<RecruitEntry> demoEntries = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** Highest KC per raid across the player's accounts, or -1 until fetched. */
	private volatile int coxKc = -1;
	private volatile int tobKc = -1;
	private volatile int toaKc = -1;
	/** Pending quick-hop target, processed on the next game tick. */
	private net.runelite.api.World quickHopTarget;
	private int quickHopAttempts;
	@Provides
	WeDoRaidsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WeDoRaidsConfig.class);
	}

	@Override
	protected void startUp()
	{
		final HostFormPanel.HostActions hostActions = new HostFormPanel.HostActions()
		{
			@Override
			public void submit(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
			{
				hostRaid(fields, status);
			}

			@Override
			public void update(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
			{
				updatePost(fields, status);
			}

			@Override
			public void close(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
			{
				closePost(fields, status);
			}
		};
		panel = new WeDoRaidsPanel(config, hostActions,
			(key, value) -> configManager.setConfiguration("wedoraids", key, value),
			this::hopTo, () -> currentWorld, () -> currentCoxLayout, () -> localPlayerName,
			this::userKcForRaid, this::fetchKc,
			this::warnHostIdle,
			() -> config.autoPartyHub(), this::joinPartyHub,
			this::rescheduleRemoteFeed, this::worldBlockReason);

		navButton = NavigationButton.builder()
			.tooltip("We Do Raids")
			.icon(loadIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		remoteFeedPoller = new RemoteFeedPoller(okHttpClient, gson, this::acceptEntries,
			this::setLocalBanned, this::setBridgeOnline, this::setLocalVerified);
		rescheduleRemoteFeed();

		final boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		runOnPanel(identityGeneration.get(), p -> p.setLoggedIn(loggedIn));

		boardOverlay = new RaidBoardOverlay(client, () -> activeTobHosts, () -> activeToaHosts);
		overlayManager.add(boardOverlay);

		if (config.demoData())
		{
			loadDemoData();
		}
		startWorldCache();
	}

	@Override
	protected void shutDown()
	{
		stopWorldCache();
		final long generation;
		synchronized (feedLifecycleLock)
		{
			cancelRemoteFeedLocked();
			synchronized (identityLock)
			{
				generation = resetIdentityFeedStateLocked();
				localPlayerName = null;
			}
			remoteFeedPoller = null;
		}
		queueIdentityPanelReset(generation);
		if (boardOverlay != null)
		{
			overlayManager.remove(boardOverlay);
			boardOverlay = null;
		}
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		if (panel != null)
		{
			panel.shutdown();
		}
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"wedoraids".equals(event.getGroup()))
		{
			return;
		}
		switch (event.getKey())
		{
			case "remoteFeedUrl":
				rescheduleRemoteFeed();
				break;
			case "remoteFeedKey":
				resetIdentityFeedState();
				if (config.demoData())
				{
					loadDemoData();
				}
				else
				{
					rescheduleRemoteFeed();
				}
				break;
			case "demoData":
				resetIdentityFeedState();
				if (config.demoData())
				{
					loadDemoData();
				}
				else
				{
					demoEntries.clear();
					runOnPanel(identityGeneration.get(), WeDoRaidsPanel::clear);
					rescheduleRemoteFeed();
				}
				break;
			default:
				break;
		}
	}

	/** Injects a spread of sample calls so the panel can be previewed without the bridge. */
	private void loadDemoData()
	{
		if (panel == null)
		{
			return;
		}
		final Instant now = Instant.now();
		// Realistic samples drawn from real WDR dumps, with the fields the parser produces.
		final java.util.List<RecruitEntry> demo = demoEntries;
		demo.clear();
		demo.add(demoEntry("Olm Enjoyer", "WDR ToB", RaidType.TOB, "Standard", null, "+2", "mdps/rdps", "trio", 447, null, "olm", "RAID", "trio w447 mdps/rdps +2 phub olm", now, 13, 84));
		demo.add(demoEntry("Pogtank Pete", "WDR ToB", RaidType.TOB, "HM Exp", "100+ kc", "+1", "mdps", "5 man", 364, null, "hmt6", "RAID", "+1 mdps pogstack 100+kc phub hmt6 w364", now, 12, 412));
		demo.add(demoEntry("Frozen Faz", "WDR ToB", RaidType.TOB, "Advanced", null, "+1", "frz", "trio", 489, null, "screen", "RAID", "+1 frz trio w489 scy pref ph screen", now, 11, 268));
		demo.add(demoEntry("Rngesus Rex", "WDR ToB", RaidType.TOB, "Standard", null, "+1", "range", null, 507, null, null, "RAID", "507+1 range", now, 10, 61));
		demo.add(demoEntry("Sanguine Sal", "WDR ToB", RaidType.TOB, "HM", "HM", "+2", "rdps/nfrz", null, 403, null, null, "RAID", "looking for 1 rdps 2 freeze w403", now, 9, 337));
		demo.add(demoEntry("Prep School", "WDR CoX", RaidType.COX, "Scaled", "3+4", "+2", null, null, 0, null, null, "RAID", "c 3+4", now, 8, 143));
		demo.add(demoEntry("Skip Steady", "WDR CoX", RaidType.COX, "FFA", "3+4", "+2", "melee", null, 0, null, null, "RAID", "+2 3+4 mele taken", now, 7, 512));
		demo.add(demoEntry("Cm Chad", "WDR CoX", RaidType.COX, "FFA CM", "CM", "+1", null, null, 0, null, "Cm Chad", "RAID", "+1 fc: Cm Chad ffa C.M", now, 6, 806));
		demo.add(demoEntry("Fresh Learner", "WDR CoX", RaidType.COX, "Learner", null, null, null, null, 0, null, null, "LFG", "LFG, new to raids never done any beside for the diary", now, 5, 3));
		demo.add(demoEntry("Invo Andy", "WDR ToA", RaidType.TOA, "300-445", "400 invo split", null, null, null, 503, null, null, "LFG", "400 w503 split all", now, 4, 221));
		demo.add(demoEntry("Kit Chaser", "WDR ToA", RaidType.TOA, "0-295", "200 invo", "+1", null, null, 362, null, null, "RAID", "200 +1 w362", now, 3, 47));
		demo.add(demoEntry("Warden Wes", "WDR ToA", RaidType.TOA, "450+", "450-500 invo", "+1", null, "duo", 520, null, null, "RAID", "+1 duos 450-500s w520", now, 2, 690));
		demo.add(demoEntry("Mog Hunter", "WDR ToA", RaidType.TOA, "450+", "450 invo kit", null, null, "trio", 0, null, null, "LFG", "any 450 duo/trio zebak transmog?", now, 1, 455));
		demo.add(demoEntry("Nylo Nick", "WDR ToB", RaidType.TOB, "HM Exp", null, "+1", "nfrz", "5 man", 516, null, "nylo", "RAID", "+1 - nfrz - w516 (pogstack) - ph: nylo", now, 0, 158));

		setLocalBanned(false);
		setLocalVerified(true);
		runOnPanel(identityGeneration.get(), p -> p.setLoggedIn(true));
		acceptEntries(java.util.Collections.emptyList());
		rescheduleRemoteFeed();
	}

	private static RecruitEntry demoEntry(String sender, String source, RaidType raid, String tier,
		String mode, String spots, String roles, String party, int world, String region, String host,
		String kind, String message, Instant now, int minutesAgo, int kc)
	{
		return new RecruitEntry(sender, source, raid, tier, mode, spots, roles, party, world, region, host,
			kc, kind, message, now.minus(Duration.ofMinutes(minutesAgo)));
	}

	/** The bridge to poll: the advanced override if set, else the built-in default. */
	private String bridgeUrl()
	{
		final String override = config.remoteFeedUrl().trim();
		return override.isEmpty() ? DEFAULT_BRIDGE_URL : override;
	}

	private void rescheduleRemoteFeed()
	{
		final RemoteFeedPoller poller;
		final String url;
		final String key;
		final String viewer;
		final long identity;
		final long pollEpoch;
		final long lifecycle;
		final boolean demo;
		synchronized (feedLifecycleLock)
		{
			cancelRemoteFeedLocked();
			poller = remoteFeedPoller;
			demo = poller != null && config.demoData();
			key = poller == null ? null : config.remoteFeedKey();
			synchronized (identityLock)
			{
				viewer = localPlayerName;
				identity = identityGeneration.get();
			}
			final boolean eligible = poller != null && !demo && !key.trim().isEmpty() && viewer != null;
			url = eligible ? bridgeUrl() : null;
			pollEpoch = eligible ? poller.pollEpoch() : 0;
			lifecycle = feedLifecycleGeneration;
		}
		if (url == null)
		{
			if (poller != null)
			{
				setBridgeStatus(BridgeStatus.OFF, lifecycle);
				if (!demo)
				{
					setLocalVerified(false);
				}
			}
			return;
		}

		setBridgeStatus(BridgeStatus.CONNECTING, lifecycle);
		installRemoteFeedTask(poller, url, key, viewer, identity, pollEpoch, lifecycle);
	}

	private void installRemoteFeedTask(RemoteFeedPoller poller, String url, String key, String viewer,
		long identity, long pollEpoch, long lifecycle)
	{
		final ScheduledFuture<?> replacement = executor.scheduleWithFixedDelay(
			() ->
			{
				if (identityGeneration.get() == identity)
				{
					poller.poll(url, key, viewer, pollEpoch, lifecycle);
				}
			},
			0, POLL_SECONDS, TimeUnit.SECONDS);
		final boolean installed;
		synchronized (feedLifecycleLock)
		{
			installed = feedLifecycleGeneration == lifecycle;
			if (installed)
			{
				remoteFeedTask = replacement;
			}
		}
		if (!installed)
		{
			replacement.cancel(false);
		}
	}

	private void setBridgeOnline(long lifecycle, boolean online)
	{
		setBridgeStatus(online ? BridgeStatus.ONLINE : BridgeStatus.OFFLINE, lifecycle);
	}

	private int userKcForRaid(RaidType raid)
	{
		switch (raid)
		{
			case COX:
				return coxKc;
			case TOB:
				return tobKc;
			case TOA:
				return toaKc;
			default:
				return -1;
		}
	}

	/** Fetches the player's per-raid max KC from the bridge, then refreshes the host form. */
	private void fetchKc()
	{
		// No key = not opted in, so never contact the bridge (and it'd return nothing anyway).
		if (config.demoData() || config.remoteFeedKey().trim().isEmpty())
		{
			return;
		}
		final long generation;
		final String viewer;
		synchronized (identityLock)
		{
			generation = identityGeneration.get();
			viewer = localPlayerName;
		}
		if (viewer == null)
		{
			return;
		}
		final String key = config.remoteFeedKey().trim();
		final HttpUrl feed = HttpUrl.parse(bridgeUrl());
		if (feed == null || feed.pathSize() == 0)
		{
			return;
		}
		final HttpUrl.Builder url = feed.newBuilder()
			.setPathSegment(feed.pathSize() - 1, "kc")
			.addQueryParameter("viewer", viewer)
			.addQueryParameter("key", key);
		okHttpClient.newCall(new Request.Builder().url(url.build()).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, java.io.IOException e)
			{
				if (isCurrentIdentity(generation, viewer))
				{
					log.debug("We Do Raids: KC fetch failed", e);
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!isCurrentIdentity(generation, viewer))
					{
						return;
					}
					if (!r.isSuccessful() || r.body() == null)
					{
						return;
					}
					final KcResult k = gson.fromJson(r.body().charStream(), KcResult.class);
					if (k != null && k.verified && commitKc(generation, viewer, k))
					{
						runOnPanel(generation, WeDoRaidsPanel::refreshHostTiers);
					}
				}
				catch (Exception e)
				{
					log.debug("We Do Raids: bad KC response", e);
				}
			}
		});
	}

	private static class KcResult
	{
		boolean verified;
		int cox;
		int tob;
		int toa;
	}

	private void setBridgeStatus(BridgeStatus status, long lifecycle)
	{
		final long identity = identityGeneration.get();
		final WeDoRaidsPanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			synchronized (feedLifecycleLock)
			{
				if (identityGeneration.get() == identity && feedLifecycleGeneration == lifecycle
					&& panel == currentPanel)
				{
					currentPanel.setBridgeStatus(status);
				}
			}
		});
	}

	private void resetIdentityFeedState()
	{
		final long generation;
		synchronized (feedLifecycleLock)
		{
			cancelRemoteFeedLocked();
			synchronized (identityLock)
			{
				generation = resetIdentityFeedStateLocked();
			}
		}
		queueIdentityPanelReset(generation);
	}

	private long resetIdentityFeedStateLocked()
	{
		final long generation = identityGeneration.incrementAndGet();
		localBanned = false;
		localVerified = false;
		coxKc = -1;
		tobKc = -1;
		toaKc = -1;
		currentCoxLayout = null;
		notifiedKeys.clear();
		activeTobHosts.clear();
		activeToaHosts.clear();
		return generation;
	}

	private void queueIdentityPanelReset(long generation)
	{
		runOnPanel(generation, p ->
		{
			p.exitHostLive();
			p.setBanned(false);
			p.setVerified(false);
			p.setEntries(java.util.Collections.emptyList());
		});
	}

	private boolean isCurrentIdentity(long generation, String viewer)
	{
		synchronized (identityLock)
		{
			return isCurrentIdentityLocked(generation, viewer);
		}
	}

	private boolean isCurrentIdentityLocked(long generation, String viewer)
	{
		return identityGeneration.get() == generation
			&& java.util.Objects.equals(localPlayerName, viewer);
	}

	private boolean commitKc(long generation, String viewer, KcResult result)
	{
		synchronized (identityLock)
		{
			if (!isCurrentIdentityLocked(generation, viewer))
			{
				return false;
			}
			coxKc = result.cox;
			tobKc = result.tob;
			toaKc = result.toa;
			return true;
		}
	}

	private void runOnPanel(long generation, java.util.function.Consumer<WeDoRaidsPanel> update)
	{
		final WeDoRaidsPanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (identityGeneration.get() == generation && panel == currentPanel)
			{
				update.accept(currentPanel);
			}
		});
	}

	private void setViewerName(String viewer)
	{
		final String normalized = viewer == null || viewer.trim().isEmpty() ? null : viewer.trim();
		final long generation;
		synchronized (feedLifecycleLock)
		{
			synchronized (identityLock)
			{
				if (java.util.Objects.equals(localPlayerName, normalized))
				{
					return;
				}
			}
			cancelRemoteFeedLocked();
			synchronized (identityLock)
			{
				generation = resetIdentityFeedStateLocked();
				localPlayerName = normalized;
			}
		}
		queueIdentityPanelReset(generation);
		runOnPanel(generation, p -> p.setLoggedIn(normalized != null));
		if (normalized == null)
		{
			return;
		}
		if (config.demoData())
		{
			setLocalVerified(true);
			acceptEntries(java.util.Collections.emptyList());
		}
		rescheduleRemoteFeed();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			final long generation;
			synchronized (feedLifecycleLock)
			{
				cancelRemoteFeedLocked();
				synchronized (identityLock)
				{
					generation = resetIdentityFeedStateLocked();
					localPlayerName = null;
				}
			}
			queueIdentityPanelReset(generation);
			runOnPanel(generation, p -> p.setLoggedIn(false));
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			runOnPanel(identityGeneration.get(), p -> p.setLoggedIn(true));
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Keep the RSN and world fresh (covers being already logged in when the plugin starts).
		final Player local = client.getLocalPlayer();
		if (local != null && local.getName() != null)
		{
			setViewerName(local.getName());
		}
		currentWorld = client.getWorld();

		// Drive a pending quick-hop, same flow the World Hopper uses.
		if (quickHopTarget == null)
		{
			return;
		}
		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();
			if (++quickHopAttempts >= 20)
			{
				quickHopTarget = null;
			}
		}
		else
		{
			client.hopToWorld(quickHopTarget);
			quickHopTarget = null;
		}
	}

	@Subscribe
	public void onWorldsFetch(WorldsFetch event)
	{
		worldSnapshotCache.accept(event.getWorldResult());
	}

	@Subscribe
	public void onRaidScouted(RaidScouted event)
	{
		// The raids plugin only scouts non-CM raids, so this is inherently CM-free.
		currentCoxLayout = buildLayoutString(event.getRaid());
		// Push the fresh layout into the host form so re-scouting a raid updates it.
		runOnPanel(identityGeneration.get(), WeDoRaidsPanel::refreshCoxLayout);
	}

	@Subscribe
	public void onRaidReset(RaidReset event)
	{
		currentCoxLayout = null;
	}

	/**
	 * Rebuilds the CoX plugin's own "[code]: rooms" layout string (the !layout format),
	 * e.g. "[SCPFCSPCPF]: Vasa, Tekton, Tightrope, Vespula, Crabs". The code is what
	 * experienced raiders read; the room names spell out the order for everyone else.
	 */
	private static String buildLayoutString(Raid raid)
	{
		if (raid == null || raid.getLayout() == null)
		{
			return null;
		}
		final StringBuilder rooms = new StringBuilder();
		for (Room r : raid.getLayout().getRooms())
		{
			final RaidRoom room = raid.getRoom(r.getPosition());
			if (room == null)
			{
				continue;
			}
			final RoomType type = room.getType();
			if (type == RoomType.COMBAT || type == RoomType.PUZZLE)
			{
				if (rooms.length() > 0)
				{
					rooms.append(", ");
				}
				rooms.append(room.getName());
			}
		}
		if (rooms.length() == 0)
		{
			return null;
		}
		return "[" + floorCode(raid) + "]: " + rooms;
	}

	/**
	 * The layout code with its two floors split by " | " (e.g. "FSCCP | PCSCF").
	 * toCode() brackets each floor as #...¤, so we split on the ¤ markers.
	 */
	private static String floorCode(Raid raid)
	{
		final StringBuilder sb = new StringBuilder();
		for (String floor : raid.getLayout().toCode().split("¤"))
		{
			final String clean = floor.replace("#", "").trim();
			if (clean.isEmpty())
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(" | ");
			}
			sb.append(clean);
		}
		return sb.length() > 0 ? sb.toString() : raid.getLayout().toCodeString();
	}

	/**
	 * The hosted raid has gone idle: desktop notification (toggleable) plus an in-game
	 * chat line so it's visible even with the side panel closed.
	 */
	private void warnHostIdle()
	{
		notifier.notify(config.hostIdleNotify(),
			"Your We Do Raids party is idle — open the plugin and click \"I'm here\" or it will auto-close.");
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(net.runelite.api.ChatMessageType.CONSOLE, "",
					"<col=e57373>We Do Raids:</col> your hosted party is idle — click \"I'm here\" in the "
						+ "side panel within 60 seconds or it will auto-close.", null);
			}
		});
	}

	/**
	 * Why a world is unsuitable for raid recruiting, or null if it's a normal world.
	 * Type-based (PvP/High Risk/BH/LMS/DMM/etc.) so the weekly PvP rotation never needs
	 * hardcoding; unknown world numbers are rejected too. Null when the world list
	 * hasn't loaded yet (can't verify — don't block).
	 */
	String worldBlockReason(int worldId)
	{
		final WorldResult worldResult = worldSnapshotCache.snapshot();
		if (worldResult == null)
		{
			return null;
		}
		final World world = worldResult.findWorld(worldId);
		if (world == null)
		{
			return "not a valid world";
		}
		return worldBlockReason(world);
	}

	private static String worldBlockReason(World world)
	{
		final java.util.EnumSet<net.runelite.http.api.worlds.WorldType> types = world.getTypes();
		if (types.contains(net.runelite.http.api.worlds.WorldType.PVP))
		{
			return "a PvP world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.HIGH_RISK))
		{
			return "a High Risk world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.BOUNTY))
		{
			return "a Bounty Hunter world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.LAST_MAN_STANDING))
		{
			return "an LMS world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.DEADMAN))
		{
			return "a Deadman world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.PVP_ARENA))
		{
			return "a PvP Arena world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.TOURNAMENT))
		{
			return "a Tournament world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.SEASONAL))
		{
			return "a Leagues world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.QUEST_SPEEDRUNNING))
		{
			return "a Speedrunning world";
		}
		if (types.contains(net.runelite.http.api.worlds.WorldType.FRESH_START_WORLD))
		{
			return "a Fresh Start world";
		}
		return null;
	}

	/** Join a callout's party hub in the RuneLite Party plugin. Starts on the EDT. */
	private void joinPartyHub(String hub)
	{
		if (hub == null || hub.trim().isEmpty())
		{
			return;
		}
		final String passphrase = hub.trim();
		clientThread.invokeLater(() -> partyService.changeParty(passphrase));
	}

	/** Quick-hop to a world clicked in the panel. Called on the EDT. */
	private void hopTo(int worldId)
	{
		if (worldId <= 0)
		{
			return;
		}
		final WorldResult worldResult = worldSnapshotCache.snapshot();
		if (worldResult == null)
		{
			return;
		}
		final World world = worldResult.findWorld(worldId);
		final String blockReason = world == null ? "not a valid world" : worldBlockReason(world);
		if (blockReason != null)
		{
			clientThread.invokeLater(() ->
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					client.addChatMessage(net.runelite.api.ChatMessageType.CONSOLE, "",
						"<col=e57373>We Do Raids:</col> not hopping to W" + worldId + " — it's " + blockReason + ".", null);
				}
			});
			return;
		}
		clientThread.invoke(() ->
		{
			final net.runelite.api.World rsWorld = client.createWorld();
			rsWorld.setActivity(world.getActivity());
			rsWorld.setAddress(world.getAddress());
			rsWorld.setId(world.getId());
			rsWorld.setPlayerCount(world.getPlayers());
			rsWorld.setLocation(world.getLocation());
			rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

			if (client.getGameState() == GameState.LOGIN_SCREEN)
			{
				client.changeWorld(rsWorld);
				return;
			}
			quickHopTarget = rsWorld;
			quickHopAttempts = 0;
		});
	}

	void startWorldCache()
	{
		worldSnapshotCache.start();
	}

	void stopWorldCache()
	{
		worldSnapshotCache.stop();
	}

	WorldResult loadWorlds()
	{
		return worldService.getWorlds();
	}

	private void setLocalBanned(boolean banned)
	{
		final long generation = identityGeneration.get();
		final boolean b = !config.demoData() && banned;
		if (localBanned == b)
		{
			return;
		}
		localBanned = b;
		runOnPanel(generation, p -> p.setBanned(b));
	}

	private void setLocalVerified(boolean verified)
	{
		final long generation = identityGeneration.get();
		final boolean v = config.demoData() || verified;
		if (localVerified == v)
		{
			return;
		}
		localVerified = v;
		runOnPanel(generation, p -> p.setVerified(v));
	}

	/**
	 * @return true if the WDR bridge flagged the local player as banned.
	 */
	boolean isLocalPlayerBanned()
	{
		return localBanned;
	}

	/**
	 * Posts a recruiting call through the bridge into the matching WDR channel.
	 * Starts on the EDT; reports the asynchronous bridge result through {@code status} on the EDT.
	 */
	private void hostRaid(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
	{
		final String partyHub = fields.get("partyHub");
		postAction("host", fields, "Posted to Discord", status, (generation, result) ->
		{
			if (result.messageId != null)
			{
				runOnPanel(generation, p -> p.enterHostLive(result.messageId));
			}
			// Auto party hub: create the RuneLite party for the posted passphrase so
			// joiners can hop in via the Party plugin. Never yanks you out of a party
			// you're already in.
			if (config.autoPartyHub() && partyHub != null && !partyHub.isEmpty())
			{
				clientThread.invokeLater(() ->
				{
					if (identityGeneration.get() == generation && !partyService.isInParty())
					{
						partyService.changeParty(partyHub);
					}
				});
			}
		});
	}

	private void updatePost(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
	{
		postAction("update", fields, "Updated", status, null);
	}

	private void closePost(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
	{
		postAction("close", fields, "Closed", status,
			(generation, result) -> runOnPanel(generation, WeDoRaidsPanel::exitHostLive));
	}

	/** Starts a host/update/close action on the EDT; bridge callbacks are asynchronous. */
	private void postAction(String endpoint, java.util.Map<String, String> fields, String okMessage,
		java.util.function.Consumer<String> status,
		java.util.function.BiConsumer<Long, HostResult> onOk)
	{
		final long generation = identityGeneration.get();
		final String viewer = localPlayerName;
		final String key = config.remoteFeedKey().trim();
		final java.util.function.Consumer<String> reply =
			msg -> SwingUtilities.invokeLater(() ->
			{
				if (isCurrentIdentity(generation, viewer))
				{
					status.accept(msg);
				}
			});

		if (localBanned)
		{
			reply.accept("You are on the WDR ban list — cannot host.");
			return;
		}
		if (viewer == null)
		{
			reply.accept("Log in first so we can post your IGN.");
			return;
		}
		if (key.isEmpty())
		{
			reply.accept("Enter your WDR verification key first.");
			return;
		}
		if (config.demoData())
		{
			reply.accept("Hosting is unavailable in demo mode.");
			return;
		}
		if (!localVerified)
		{
			reply.accept("Your WDR account is not verified.");
			return;
		}
		final HttpUrl feed = HttpUrl.parse(bridgeUrl());
		if (feed == null || feed.pathSize() == 0)
		{
			reply.accept("Invalid feed URL.");
			return;
		}

		final HttpUrl.Builder url = feed.newBuilder().setPathSegment(feed.pathSize() - 1, endpoint);
		url.addQueryParameter("key", key);

		final java.util.Map<String, String> body = new java.util.LinkedHashMap<>(fields);
		body.put("ign", viewer);
		body.put("viewer", viewer);

		final Request request = new Request.Builder()
			.url(url.build())
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, java.io.IOException e)
			{
				reply.accept("Could not reach the bridge.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!isCurrentIdentity(generation, viewer))
					{
						return;
					}
					final HostResult result = r.body() != null
						? gson.fromJson(r.body().charStream(), HostResult.class) : null;
					if (r.isSuccessful() && result != null && result.ok)
					{
						reply.accept(okMessage);
						if (onOk != null)
						{
							onOk.accept(generation, result);
						}
					}
					else if (result != null && result.error != null)
					{
						reply.accept(result.error);
					}
					else
					{
						reply.accept("Failed (" + r.code() + ").");
					}
				}
				catch (Exception e)
				{
					reply.accept("Failed: bad response.");
				}
			}
		});
	}

	private static class HostResult
	{
		boolean ok;
		String posted;
		String messageId;
		String error;
	}

	private void cancelRemoteFeedLocked()
	{
		feedLifecycleGeneration++;
		if (remoteFeedPoller != null)
		{
			remoteFeedPoller.cancel();
		}
		if (remoteFeedTask != null)
		{
			remoteFeedTask.cancel(false);
			remoteFeedTask = null;
		}
	}

	/** Config-driven visibility gate applied to every call (banned/verified/raid/tier/kind/keyword). */
	private boolean passesFilters(RecruitEntry entry)
	{
		return !localBanned
			&& localVerified
			&& raidEnabled(entry.getRaidType())
			&& matchesKeywordFilter(entry.getMessage())
			&& kindEnabled(entry.getKind())
			&& matchesTierFilter(entry.getTier());
	}

	/**
	 * Mirrors the bridge's full current feed into the panel each poll, so deleted or
	 * closed raids disappear. Pings only for calls that are both new to us and fresh.
	 */
	private void acceptEntries(java.util.List<RecruitEntry> list)
	{
		final long generation = identityGeneration.get();
		final FeedProjection projection = FeedProjection.project(list, demoEntries, this::passesFilters,
			config.demoData());
		final java.util.Set<String> current = new java.util.HashSet<>();
		for (RecruitEntry e : projection.getNotifiableLiveEntries())
		{
			final String key = e.getSender() + '|' + e.getRaidType() + '|'
				+ e.getTimestamp().toEpochMilli() + '|' + e.getMessage();
			if (current.add(key) && !notifiedKeys.contains(key)
				&& Duration.between(e.getTimestamp(), Instant.now()).toMinutes() < 2)
			{
				if (identityGeneration.get() == generation)
				{
					notifyRecruit(e);
				}
			}
		}
		if (identityGeneration.get() != generation)
		{
			return;
		}
		// Bound the set to what's currently open (re-appearing calls may ping again).
		notifiedKeys.clear();
		notifiedKeys.addAll(current);

		activeTobHosts.clear();
		activeTobHosts.addAll(projection.getTobHosts());
		activeToaHosts.clear();
		activeToaHosts.addAll(projection.getToaHosts());

		runOnPanel(generation, p -> p.setEntries(projection.getEntries()));
	}

	void notifyRecruit(RecruitEntry entry)
	{
		notifier.notify(config.notifyOnRecruit(), entry.getSender() + " is recruiting: " + entry.getMessage());
	}

	private boolean matchesTierFilter(String tier)
	{
		final String filter = config.tierFilter().trim();
		if (filter.isEmpty())
		{
			return true;
		}
		// Entries with no tier (in-game / untiered raids) always pass; only tiered
		// entries are gated, so you can hide the WDR tiers above your KC.
		if (tier == null)
		{
			return true;
		}
		for (String allowed : filter.split(","))
		{
			if (allowed.trim().equalsIgnoreCase(tier))
			{
				return true;
			}
		}
		return false;
	}

	private boolean kindEnabled(String kind)
	{
		// Only actionable adverts and LFG posts; bare joins / role offers are noise.
		return "RAID".equals(kind) || "LFG".equals(kind);
	}

	private boolean raidEnabled(RaidType raidType)
	{
		switch (raidType)
		{
			case TOB:
				return config.showTob();
			case COX:
				return config.showCox();
			case TOA:
				return config.showToa();
			default:
				return true;
		}
	}

	private boolean matchesKeywordFilter(String message)
	{
		final String filter = config.keywordFilter().trim();
		if (filter.isEmpty())
		{
			return true;
		}

		final String lower = message.toLowerCase();
		for (String keyword : filter.split(","))
		{
			keyword = keyword.trim().toLowerCase();
			if (!keyword.isEmpty() && lower.contains(keyword))
			{
				return true;
			}
		}
		return false;
	}

	/** Sidebar icon: the WDR logo, falling back to raid-colored dots if the resource is missing. */
	private BufferedImage loadIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(getClass(), "wdr.png");
		}
		catch (RuntimeException e)
		{
			log.debug("We Do Raids: wdr.png resource missing, using drawn fallback", e);
			return drawnIcon();
		}
	}

	private static BufferedImage drawnIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(1f));

		g.setColor(RaidType.TOB.getColor());
		g.fillOval(0, 1, 7, 7);
		g.setColor(RaidType.COX.getColor());
		g.fillOval(9, 1, 7, 7);
		g.setColor(RaidType.TOA.getColor());
		g.fillOval(4, 8, 7, 7);

		g.dispose();
		return image;
	}
}
