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
import com.wedoraids.WeDoRaidsConfig;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public final class BridgeClient
{
	@FunctionalInterface
	public interface KcCommitter
	{
		boolean commit(long generation, String viewer, KcResult result);
	}

	public static final class IdentitySnapshot
	{
		private final long generation;
		private final String viewer;

		public IdentitySnapshot(long generation, String viewer)
		{
			this.generation = generation;
			this.viewer = viewer;
		}
	}

	public static final class KcResult
	{
		boolean verified;
		public int cox;
		public int tob;
		public int toa;
	}

	public static final class HostResult
	{
		boolean ok;
		public String messageId;
		String error;
	}

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private final WeDoRaidsConfig config;
	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final Supplier<String> bridgeUrl;
	private final Supplier<IdentitySnapshot> identitySnapshot;
	private final LongSupplier identityGeneration;
	private final Supplier<String> viewer;
	private final BooleanSupplier banned;
	private final BooleanSupplier verified;
	private final BiPredicate<Long, String> currentIdentity;
	private final KcCommitter kcCommitter;
	private final LongConsumer refreshHostTiers;

	public BridgeClient(WeDoRaidsConfig config, OkHttpClient okHttpClient, Gson gson, Supplier<String> bridgeUrl,
		Supplier<IdentitySnapshot> identitySnapshot, LongSupplier identityGeneration, Supplier<String> viewer,
		BooleanSupplier banned, BooleanSupplier verified, BiPredicate<Long, String> currentIdentity,
		KcCommitter kcCommitter, LongConsumer refreshHostTiers)
	{
		this.config = config;
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.bridgeUrl = bridgeUrl;
		this.identitySnapshot = identitySnapshot;
		this.identityGeneration = identityGeneration;
		this.viewer = viewer;
		this.banned = banned;
		this.verified = verified;
		this.currentIdentity = currentIdentity;
		this.kcCommitter = kcCommitter;
		this.refreshHostTiers = refreshHostTiers;
	}

	public void fetchKc()
	{
		if (config.demoData() || config.remoteFeedKey().trim().isEmpty())
		{
			return;
		}
		final IdentitySnapshot snapshot = identitySnapshot.get();
		if (snapshot.viewer == null)
		{
			return;
		}
		final String key = config.remoteFeedKey().trim();
		final HttpUrl feed = HttpUrl.parse(bridgeUrl.get());
		if (feed == null || feed.pathSize() == 0)
		{
			return;
		}
		final HttpUrl.Builder url = feed.newBuilder()
			.setPathSegment(feed.pathSize() - 1, "kc")
			.addQueryParameter("viewer", snapshot.viewer)
			.addQueryParameter("key", key);
		okHttpClient.newCall(new Request.Builder().url(url.build()).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				if (currentIdentity.test(snapshot.generation, snapshot.viewer))
				{
					log.debug("We Do Raids: KC fetch failed", exception);
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response currentResponse = response)
				{
					if (!currentIdentity.test(snapshot.generation, snapshot.viewer))
					{
						return;
					}
					if (!currentResponse.isSuccessful() || currentResponse.body() == null)
					{
						return;
					}
					final KcResult result = gson.fromJson(currentResponse.body().charStream(), KcResult.class);
					if (result != null && result.verified
						&& kcCommitter.commit(snapshot.generation, snapshot.viewer, result))
					{
						refreshHostTiers.accept(snapshot.generation);
					}
				}
				catch (Exception exception)
				{
					log.debug("We Do Raids: bad KC response", exception);
				}
			}
		});
	}

	public void postAction(String endpoint, Map<String, String> fields, String okMessage,
		Consumer<String> status, BiConsumer<Long, HostResult> onOk)
	{
		final long generation = identityGeneration.getAsLong();
		final String viewerName = viewer.get();
		final String key = config.remoteFeedKey().trim();
		final Consumer<String> reply = message -> SwingUtilities.invokeLater(() ->
		{
			if (currentIdentity.test(generation, viewerName))
			{
				status.accept(message);
			}
		});
		if (banned.getAsBoolean())
		{
			reply.accept("You are on the WDR ban list and cannot host.");
			return;
		}
		if (viewerName == null)
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
		if (!verified.getAsBoolean())
		{
			reply.accept("Your WDR account is not verified.");
			return;
		}
		final HttpUrl feed = HttpUrl.parse(bridgeUrl.get());
		if (feed == null || feed.pathSize() == 0)
		{
			reply.accept("Invalid feed URL.");
			return;
		}
		final HttpUrl.Builder url = feed.newBuilder().setPathSegment(feed.pathSize() - 1, endpoint);
		url.addQueryParameter("key", key);
		final Map<String, String> body = new LinkedHashMap<>(fields);
		body.put("ign", viewerName);
		body.put("viewer", viewerName);
		final Request request = new Request.Builder()
			.url(url.build())
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				reply.accept("Could not reach the bridge.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response currentResponse = response)
				{
					if (!currentIdentity.test(generation, viewerName))
					{
						return;
					}
					final HostResult result = currentResponse.body() != null
						? gson.fromJson(currentResponse.body().charStream(), HostResult.class) : null;
					if (currentResponse.isSuccessful() && result != null && result.ok)
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
						reply.accept("Failed (" + currentResponse.code() + ").");
					}
				}
				catch (Exception exception)
				{
					reply.accept("Failed: bad response.");
				}
			}
		});
	}
}
