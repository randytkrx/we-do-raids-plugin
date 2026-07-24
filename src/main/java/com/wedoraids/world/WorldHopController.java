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

import java.util.EnumSet;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

public final class WorldHopController
{
	private final Client client;
	private final ClientThread clientThread;
	private final WorldSnapshotCache worldSnapshotCache;
	private net.runelite.api.World quickHopTarget;
	private int quickHopAttempts;

	public WorldHopController(Client client, ClientThread clientThread, WorldSnapshotCache worldSnapshotCache)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.worldSnapshotCache = worldSnapshotCache;
	}

	public void onGameTick()
	{
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

	public String worldBlockReason(int worldId)
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

	public void hopTo(int worldId)
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
					client.addChatMessage(ChatMessageType.CONSOLE, "",
						"<col=e57373>We Do Raids:</col> not hopping to W" + worldId + ", it's "
							+ blockReason + ".", null);
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

	private static String worldBlockReason(World world)
	{
		final EnumSet<WorldType> types = world.getTypes();
		if (types.contains(WorldType.PVP))
		{
			return "a PvP world";
		}
		if (types.contains(WorldType.HIGH_RISK))
		{
			return "a High Risk world";
		}
		if (types.contains(WorldType.BOUNTY))
		{
			return "a Bounty Hunter world";
		}
		if (types.contains(WorldType.LAST_MAN_STANDING))
		{
			return "an LMS world";
		}
		if (types.contains(WorldType.DEADMAN))
		{
			return "a Deadman world";
		}
		if (types.contains(WorldType.PVP_ARENA))
		{
			return "a PvP Arena world";
		}
		if (types.contains(WorldType.TOURNAMENT))
		{
			return "a Tournament world";
		}
		if (types.contains(WorldType.SEASONAL))
		{
			return "a Leagues world";
		}
		if (types.contains(WorldType.QUEST_SPEEDRUNNING))
		{
			return "a Speedrunning world";
		}
		if (types.contains(WorldType.FRESH_START_WORLD))
		{
			return "a Fresh Start world";
		}
		return null;
	}
}
