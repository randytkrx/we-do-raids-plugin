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

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.party.PartyService;

final class HostInteractionController
{
	private final WeDoRaidsConfig config;
	private final Supplier<BridgeClient> bridgeClient;
	private final Client client;
	private final ClientThread clientThread;
	private final Notifier notifier;
	private final PartyService partyService;
	private final LongSupplier identityGeneration;
	private final Supplier<String> viewer;
	private final Consumer<String> hostLiveOwner;
	private final BiConsumer<Long, Consumer<WeDoRaidsPanel>> panelUpdater;

	HostInteractionController(WeDoRaidsConfig config, Supplier<BridgeClient> bridgeClient, Client client,
		ClientThread clientThread, Notifier notifier, PartyService partyService, LongSupplier identityGeneration,
		Supplier<String> viewer, Consumer<String> hostLiveOwner,
		BiConsumer<Long, Consumer<WeDoRaidsPanel>> panelUpdater)
	{
		this.config = config;
		this.bridgeClient = bridgeClient;
		this.client = client;
		this.clientThread = clientThread;
		this.notifier = notifier;
		this.partyService = partyService;
		this.identityGeneration = identityGeneration;
		this.viewer = viewer;
		this.hostLiveOwner = hostLiveOwner;
		this.panelUpdater = panelUpdater;
	}

	void hostRaid(Map<String, String> fields, Consumer<String> status)
	{
		final String partyHub = fields.get("partyHub");
		bridgeClient.get().postAction("host", fields, "Posted to Discord", status, (generation, result) ->
		{
			if (result.messageId != null)
			{
				hostLiveOwner.accept(viewer.get());
				panelUpdater.accept(generation, panel -> panel.enterHostLive(result.messageId));
			}
			if (config.autoPartyHub() && partyHub != null && !partyHub.isEmpty())
			{
				clientThread.invokeLater(() ->
				{
					if (identityGeneration.getAsLong() == generation && !partyService.isInParty())
					{
						partyService.changeParty(partyHub);
					}
				});
			}
		});
	}

	void updatePost(Map<String, String> fields, Consumer<String> status)
	{
		bridgeClient.get().postAction("update", fields, "Updated", status, null);
	}

	void closePost(Map<String, String> fields, Consumer<String> status)
	{
		bridgeClient.get().postAction("close", fields, "Closed", status, (generation, result) ->
		{
			hostLiveOwner.accept(null);
			panelUpdater.accept(generation, WeDoRaidsPanel::exitHostLive);
		});
	}

	void warnHostIdle()
	{
		notifier.notify(config.hostIdleNotify(),
			"Your We Do Raids party is idle. Open the plugin and click \"I'm here\" or it will auto-close.");
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.CONSOLE, "",
					"<col=e57373>We Do Raids:</col> your hosted party is idle, click \"I'm here\" in the "
						+ "side panel within 60 seconds or it will auto-close.", null);
			}
		});
	}

	void joinPartyHub(String hub)
	{
		if (hub == null || hub.trim().isEmpty())
		{
			return;
		}
		final String passphrase = hub.trim();
		clientThread.invokeLater(() -> partyService.changeParty(passphrase));
	}
}
