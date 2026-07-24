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
package com.wedoraids.bridge;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.wedoraids.feed.RaidType;
import com.wedoraids.feed.RecruitEntry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Polls an external bridge (the WDR Discord bot on the VPS) for recruiting calls
 * and forwards the bridge's full current list to the panel on every poll.
 */
@Slf4j
public class RemoteFeedPoller
{
	@FunctionalInterface
	public interface BridgeStatusListener
	{
		void onBridgeStatus(long lifecycle, boolean online);
	}

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	/** Called each poll with the bridge's full current list (the source of truth). */
	private final Consumer<List<RecruitEntry>> onEntries;
	private final Consumer<Boolean> onBanned;
	/** Called after each poll with whether the bridge responded successfully. */
	private final BridgeStatusListener onStatus;
	/** Called with whether the bridge considers this player verified. */
	private final Consumer<Boolean> onVerified;
	private final Object requestLock = new Object();
	private final Object publicationLock = new Object();
	private Call activeCall;
	private long pollRequestGeneration;

	public RemoteFeedPoller(OkHttpClient okHttpClient, Gson gson, Consumer<List<RecruitEntry>> onEntries,
		Consumer<Boolean> onBanned, BridgeStatusListener onStatus, Consumer<Boolean> onVerified)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.onEntries = onEntries;
		this.onBanned = onBanned;
		this.onStatus = onStatus;
		this.onVerified = onVerified;
	}

	public void poll(String url, String key, String viewer, long pollRequestGeneration, long lifecycle)
	{
		final HttpUrl parsed = url == null ? null : HttpUrl.parse(url.trim());
		if (parsed == null)
		{
			log.debug("We Do Raids: invalid remote feed URL: {}", url);
			publishIfCurrent(pollRequestGeneration, () -> onStatus.onBridgeStatus(lifecycle, false));
			return;
		}

		HttpUrl.Builder builder = parsed.newBuilder();
		if (key != null && !key.trim().isEmpty())
		{
			builder.addQueryParameter("key", key.trim());
		}
		if (viewer != null && !viewer.trim().isEmpty())
		{
			// Sent so the bridge can enforce the ban list server-side for this player.
			builder.addQueryParameter("viewer", viewer.trim());
		}

		final Request request = new Request.Builder().url(builder.build()).build();
		final Call requestCall;
		synchronized (requestLock)
		{
			if (pollRequestGeneration != this.pollRequestGeneration || activeCall != null)
			{
				return;
			}
			requestCall = okHttpClient.newCall(request);
			activeCall = requestCall;
		}

		try
		{
			requestCall.enqueue(new Callback()
			{
				@Override
				public void onFailure(Call call, IOException e)
				{
					log.debug("We Do Raids: remote feed request failed", e);
					dispatch(requestCall, pollRequestGeneration, () -> onStatus.onBridgeStatus(lifecycle, false));
				}

				@Override
				public void onResponse(Call call, Response response)
				{
					try (Response r = response)
					{
						if (!r.isSuccessful() || r.body() == null)
						{
							log.debug("We Do Raids: remote feed returned {}", r.code());
							dispatch(requestCall, pollRequestGeneration, () -> onStatus.onBridgeStatus(lifecycle, false));
							return;
						}

						final FeedResponse feed = gson.fromJson(r.body().charStream(), FeedResponse.class);
						if (feed == null)
						{
							dispatch(requestCall, pollRequestGeneration, () -> onStatus.onBridgeStatus(lifecycle, false));
							return;
						}

						final List<RecruitEntry> out = new ArrayList<>();
						if (feed.entries != null)
						{
							for (FeedEntry fe : feed.entries)
							{
								final RecruitEntry entry = convert(fe);
								if (entry != null)
								{
									out.add(entry);
								}
							}
						}

						dispatch(requestCall, pollRequestGeneration, () ->
						{
							onStatus.onBridgeStatus(lifecycle, true);
							if (feed.viewerVerified != null)
							{
								onVerified.accept(feed.viewerVerified);
							}
							if (feed.viewerBanned != null)
							{
								onBanned.accept(feed.viewerBanned);
							}
							onEntries.accept(out);
						});
					}
					catch (Exception e)
					{
						log.debug("We Do Raids: failed to parse remote feed", e);
						dispatch(requestCall, pollRequestGeneration, () -> onStatus.onBridgeStatus(lifecycle, false));
					}
				}
			});
		}
		catch (RuntimeException e)
		{
			log.debug("We Do Raids: failed to enqueue remote feed request", e);
			dispatch(requestCall, pollRequestGeneration, () -> onStatus.onBridgeStatus(lifecycle, false));
		}
	}

	public long pollRequestGeneration()
	{
		synchronized (requestLock)
		{
			return pollRequestGeneration;
		}
	}

	public void cancel()
	{
		final Call call;
		synchronized (requestLock)
		{
			pollRequestGeneration++;
			call = activeCall;
			activeCall = null;
		}
		if (call != null)
		{
			call.cancel();
		}
		awaitPublicationBoundary();
	}

	private void dispatch(Call call, long pollRequestGeneration, Runnable callback)
	{
		synchronized (publicationLock)
		{
			if (!claimCurrent(call, pollRequestGeneration))
			{
				return;
			}
			callback.run();
		}
	}

	private void awaitPublicationBoundary()
	{
		synchronized (publicationLock)
		{
		}
	}

	private void publishIfCurrent(long pollRequestGeneration, Runnable callback)
	{
		synchronized (publicationLock)
		{
			synchronized (requestLock)
			{
				if (this.pollRequestGeneration != pollRequestGeneration || activeCall != null)
				{
					return;
				}
			}
			callback.run();
		}
	}

	private boolean claimCurrent(Call call, long pollRequestGeneration)
	{
		synchronized (requestLock)
		{
			if (activeCall != call || this.pollRequestGeneration != pollRequestGeneration)
			{
				return false;
			}
			activeCall = null;
			return true;
		}
	}

	private static RecruitEntry convert(FeedEntry fe)
	{
		if (fe == null || fe.sender == null || fe.raidType == null || fe.message == null)
		{
			return null;
		}

		RaidType raidType;
		try
		{
			raidType = RaidType.valueOf(fe.raidType);
		}
		catch (IllegalArgumentException ex)
		{
			raidType = RaidType.OTHER;
		}

		final Instant timestamp = fe.timestamp > 0 ? Instant.ofEpochMilli(fe.timestamp) : Instant.now();
		final String source = fe.source != null ? fe.source : "Discord";
		final String kind = fe.kind != null ? fe.kind : "RAID";

		return new RecruitEntry(fe.sender, source, raidType, fe.tier, fe.mode, fe.spots, fe.roles,
			fe.duoTrio, fe.world, fe.region, fe.host, fe.kc, kind, fe.message, timestamp);
	}

	private static class FeedResponse
	{
		Boolean viewerVerified;
		Boolean viewerBanned;
		List<FeedEntry> entries;
	}

	private static class FeedEntry
	{
		String sender;
		String source;
		String raidType;
		@Nullable
		String tier;
		@Nullable
		String mode;
		@Nullable
		String spots;
		@Nullable
		String roles;
		@Nullable
		String duoTrio;
		int world;
		@Nullable
		String region;
		@Nullable
		String host;
		int kc;
		@Nullable
		String kind;
		String message;
		@SerializedName("timestamp")
		long timestamp;
	}
}
