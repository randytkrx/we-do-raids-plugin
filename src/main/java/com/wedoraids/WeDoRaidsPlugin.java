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
import com.wedoraids.board.RaidBoardOverlay;
import com.wedoraids.bridge.BridgeClient;
import com.wedoraids.bridge.BridgeStatus;
import com.wedoraids.bridge.RemoteFeedPoller;
import com.wedoraids.feed.DemoRecruitEntries;
import com.wedoraids.feed.RaidType;
import com.wedoraids.feed.RecruitEntry;
import com.wedoraids.feed.RecruitmentCoordinator;
import com.wedoraids.host.CoxLayoutFormatter;
import com.wedoraids.host.HostDependencies;
import com.wedoraids.host.HostFormPanel;
import com.wedoraids.host.HostInteractionController;
import com.wedoraids.panel.PanelDependencies;
import com.wedoraids.panel.WeDoRaidsPanel;
import com.wedoraids.ui.PluginIcon;
import com.wedoraids.world.WorldHopController;
import com.wedoraids.world.WorldSnapshotCache;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.WorldsFetch;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.raids.events.RaidReset;
import net.runelite.client.plugins.raids.events.RaidScouted;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.Notifier;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "We Do Raids",
	description = "The official We Do Raids Discord plugin: find, join and host WDR raid recruitment (ToB/CoX/ToA) in-game",
	tags = {"wdr", "raids", "tob", "cox", "toa", "lfg", "lfm", "recruit", "party", "team"}
)
public class WeDoRaidsPlugin extends Plugin
{
	/** Default We Do Raids bridge; overridable via the advanced config for testing. */
	private static final String DEFAULT_BRIDGE_URL = "https://wdr.timecapsule.ink/recruits";
	/** Fixed feed refresh interval (seconds). Locked, not user-configurable. */
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
	private BridgeClient bridgeClient;
	private HostInteractionController hostInteractionController;
	private RecruitmentCoordinator recruitmentCoordinator;
	private WorldHopController worldHopController;
	private ScheduledFuture<?> remoteFeedTask;
	/** Owns feed task replacement; it may nest identityLock, never the reverse. */
	private final Object feedLifecycleLock = new Object();
	private long feedScheduleGeneration;
	/** Keys of calls we've already notified about, so we don't re-ping every poll. */
	private final java.util.Set<String> notifiedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** Normalized host names with an active callout, for the ToB/ToA party-board highlight. */
	private final java.util.Set<String> activeTobHosts = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.Set<String> activeToaHosts = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Local player's RSN, or null when logged out; identifies bridge requests. */
	private volatile String localPlayerName;
	/**
	 * RSN that owns the currently live hosted post, or null if none is live. The post outlives a
	 * logout so the host can log back in and still update or close it; it is dropped when a
	 * different account logs in, so one account never inherits another's post.
	 */
	private volatile String hostLiveOwner;
	/** The world the player is currently on, or 0 when logged out. */
	private volatile int currentWorld;
	/** Latest scouted CoX layout (non-CM only; the raids plugin never scouts CM). */
	private volatile String currentCoxLayout;
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
	@Provides
	WeDoRaidsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WeDoRaidsConfig.class);
	}

	@Override
	protected void startUp()
	{
		worldHopController();
		final HostFormPanel.HostActions hostActions = buildHostActions();
		panel = buildPanel(hostActions);

		navButton = NavigationButton.builder()
			.tooltip("We Do Raids")
			.icon(PluginIcon.load(getClass()))
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

	private HostFormPanel.HostActions buildHostActions()
	{
		return new HostFormPanel.HostActions()
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
	}

	private WeDoRaidsPanel buildPanel(HostFormPanel.HostActions hostActions)
	{
		final HostDependencies hostDependencies = new HostDependencies(hostActions,
			() -> currentWorld, () -> currentCoxLayout, () -> localPlayerName,
			this::userKcForRaid, this::fetchKc, this::warnHostIdle,
			() -> config.autoPartyHub(), this::worldBlockReason);
		final PanelDependencies panelDependencies = new PanelDependencies(
			(key, value) -> configManager.setConfiguration("wedoraids", key, value),
			this::hopTo, this::joinPartyHub, this::rescheduleRemoteFeed);
		return new WeDoRaidsPanel(config, hostDependencies, panelDependencies);
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
		hostLiveOwner = null;
		queueIdentityPanelReset(generation, true);
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
		final java.util.List<RecruitEntry> demo = demoEntries;
		demo.clear();
		demo.addAll(DemoRecruitEntries.create(now));

		setLocalBanned(false);
		setLocalVerified(true);
		runOnPanel(identityGeneration.get(), p -> p.setLoggedIn(true));
		acceptEntries(java.util.Collections.emptyList());
		rescheduleRemoteFeed();
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
		final long pollRequestGeneration;
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
			pollRequestGeneration = eligible ? poller.pollRequestGeneration() : 0;
			lifecycle = feedScheduleGeneration;
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
		installRemoteFeedTask(poller, url, key, viewer, identity, pollRequestGeneration, lifecycle);
	}

	private void installRemoteFeedTask(RemoteFeedPoller poller, String url, String key, String viewer,
		long identity, long pollRequestGeneration, long lifecycle)
	{
		final ScheduledFuture<?> replacement = executor.scheduleWithFixedDelay(
			() ->
			{
				if (identityGeneration.get() == identity)
				{
					poller.poll(url, key, viewer, pollRequestGeneration, lifecycle);
				}
			},
			0, POLL_SECONDS, TimeUnit.SECONDS);
		final boolean installed;
		synchronized (feedLifecycleLock)
		{
			installed = feedScheduleGeneration == lifecycle;
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
		bridgeClient().fetchKc();
	}

	private BridgeClient bridgeClient()
	{
		if (bridgeClient == null)
		{
			bridgeClient = new BridgeClient(config, okHttpClient, gson, this::bridgeUrl,
				this::identitySnapshot, identityGeneration::get, () -> localPlayerName,
				() -> localBanned, () -> localVerified, this::isCurrentIdentity, this::commitKc,
				generation -> runOnPanel(generation, WeDoRaidsPanel::refreshHostTiers));
		}
		return bridgeClient;
	}

	private BridgeClient.IdentitySnapshot identitySnapshot()
	{
		synchronized (identityLock)
		{
			return new BridgeClient.IdentitySnapshot(identityGeneration.get(), localPlayerName);
		}
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
				if (identityGeneration.get() == identity && feedScheduleGeneration == lifecycle
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
		// Key change or demo toggle: the WDR identity behind the post changed, so drop it.
		hostLiveOwner = null;
		queueIdentityPanelReset(generation, true);
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

	/**
	 * Clears identity-scoped panel state. {@code dropLivePost} is false for a plain logout, so a
	 * hosted post survives until the same account logs back in; it is true when a different
	 * account takes over, so the new account never sees the previous one's post.
	 */
	private void queueIdentityPanelReset(long generation, boolean dropLivePost)
	{
		runOnPanel(generation, p ->
		{
			if (dropLivePost)
			{
				p.exitHostLive();
			}
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

	private boolean commitKc(long generation, String viewer, BridgeClient.KcResult result)
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
		// Keep a live hosted post only for the account that created it: logging out leaves it
		// intact (normalized == null), logging back in on the same account keeps it, and any
		// other account clears it.
		final String liveOwner = hostLiveOwner;
		final boolean dropLivePost = liveOwner != null && normalized != null && !liveOwner.equals(normalized);
		if (dropLivePost)
		{
			hostLiveOwner = null;
		}
		queueIdentityPanelReset(generation, dropLivePost);
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
			// Logout keeps any live hosted post so the host can log back in and still close it.
			queueIdentityPanelReset(generation, false);
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
		worldHopController().onGameTick();
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
		currentCoxLayout = CoxLayoutFormatter.format(event.getRaid());
		// Push the fresh layout into the host form so re-scouting a raid updates it.
		runOnPanel(identityGeneration.get(), WeDoRaidsPanel::refreshCoxLayout);
	}

	@Subscribe
	public void onRaidReset(RaidReset event)
	{
		currentCoxLayout = null;
	}

	/**
	 * The hosted raid has gone idle: desktop notification (toggleable) plus an in-game
	 * chat line so it's visible even with the side panel closed.
	 */
	private void warnHostIdle()
	{
		hostInteractionController().warnHostIdle();
	}

	/**
	 * Why a world is unsuitable for raid recruiting, or null if it's a normal world.
	 * Type-based (PvP/High Risk/BH/LMS/DMM/etc.) so the weekly PvP rotation never needs
	 * hardcoding; unknown world numbers are rejected too. Null when the world list
	 * hasn't loaded yet (can't verify, don't block).
	 */
	String worldBlockReason(int worldId)
	{
		return worldHopController().worldBlockReason(worldId);
	}

	/** Join a callout's party hub in the RuneLite Party plugin. Starts on the EDT. */
	private void joinPartyHub(String hub)
	{
		hostInteractionController().joinPartyHub(hub);
	}

	/** Quick-hop to a world clicked in the panel. Called on the EDT. */
	private void hopTo(int worldId)
	{
		worldHopController().hopTo(worldId);
	}

	private WorldHopController worldHopController()
	{
		if (worldHopController == null)
		{
			worldHopController = new WorldHopController(client, clientThread, worldSnapshotCache);
		}
		return worldHopController;
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
	 * Posts a recruiting call through the bridge into the matching WDR channel.
	 * Starts on the EDT; reports the asynchronous bridge result through {@code status} on the EDT.
	 */
	private void hostRaid(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
	{
		hostInteractionController().hostRaid(fields, status);
	}

	private void updatePost(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
	{
		hostInteractionController().updatePost(fields, status);
	}

	private void closePost(java.util.Map<String, String> fields, java.util.function.Consumer<String> status)
	{
		hostInteractionController().closePost(fields, status);
	}

	private HostInteractionController hostInteractionController()
	{
		if (hostInteractionController == null)
		{
			hostInteractionController = new HostInteractionController(config, this::bridgeClient, client,
				clientThread, notifier, partyService, identityGeneration::get, () -> localPlayerName,
				owner -> hostLiveOwner = owner, this::runOnPanel);
		}
		return hostInteractionController;
	}

	private void cancelRemoteFeedLocked()
	{
		feedScheduleGeneration++;
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

	/**
	 * Mirrors the bridge's full current feed into the panel each poll, so deleted or
	 * closed raids disappear. Pings only for calls that are both new to us and fresh.
	 */
	private void acceptEntries(java.util.List<RecruitEntry> list)
	{
		recruitmentCoordinator().accept(list);
	}

	void notifyRecruit(RecruitEntry entry)
	{
		notifier.notify(config.notifyOnRecruit(), entry.getSender() + " is recruiting: " + entry.getMessage());
	}

	private RecruitmentCoordinator recruitmentCoordinator()
	{
		if (recruitmentCoordinator == null)
		{
			recruitmentCoordinator = new RecruitmentCoordinator(config, demoEntries, notifiedKeys,
				activeTobHosts, activeToaHosts, () -> localBanned, () -> localVerified,
				identityGeneration::get, this::notifyRecruit, this::runOnPanel);
		}
		return recruitmentCoordinator;
	}

}
