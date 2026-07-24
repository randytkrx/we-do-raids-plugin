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

import static com.wedoraids.LifecyclePluginFixtures.setField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import okhttp3.EventListener;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class RemoteFeedPollerTest
{
	private static final Consumer<List<RecruitEntry>> IGNORE_ENTRIES = values ->
	{
	};
	private static final Consumer<Boolean> IGNORE_BOOLEAN = value ->
	{
	};
	private static final BridgeStatusListener IGNORE_STATUS = (lifecycle, value) ->
	{
	};

	@Test
	public void malformedResponseReportsOffline()
		throws Exception
	{
		CountDownLatch completed = new CountDownLatch(1);
		List<Boolean> statuses = new ArrayList<>();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			return response(chain, "not json");
		}).build();
		RemoteFeedPoller poller = new RemoteFeedPoller(client, new Gson(), IGNORE_ENTRIES, IGNORE_BOOLEAN,
			(lifecycle, value) ->
		{
			statuses.add(value);
			completed.countDown();
		}, IGNORE_BOOLEAN);

		poll(poller, "http://bridge.test/recruits");

		assertTrue(completed.await(5, TimeUnit.SECONDS));
		assertEquals(1, statuses.size());
		assertFalse(statuses.get(0));
	}

	@Test
	public void invalidUrlReportsOffline()
	{
		List<Boolean> statuses = new ArrayList<>();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
			response(chain, "{}"))
			.build();
		RemoteFeedPoller poller = new RemoteFeedPoller(client, new Gson(), IGNORE_ENTRIES, IGNORE_BOOLEAN,
			(lifecycle, value) -> statuses.add(value), IGNORE_BOOLEAN);

		poll(poller, "not a URL");

		assertEquals(1, statuses.size());
		assertFalse(statuses.get(0));
	}

	@Test
	public void statusCarriesLifecycleSuppliedByItsPoll()
	{
		List<Long> lifecycles = new ArrayList<>();
		RemoteFeedPoller poller = new RemoteFeedPoller(new OkHttpClient(), new Gson(), IGNORE_ENTRIES,
			IGNORE_BOOLEAN, (lifecycle, online) -> lifecycles.add(lifecycle), IGNORE_BOOLEAN);

		poller.poll("not a URL", "secret", "Alice", poller.pollRequestGeneration(), 42L);

		assertEquals(Collections.singletonList(42L), lifecycles);
	}

	@Test
	public void concurrentPollCallsDoNotOverlapRequests()
		throws Exception
	{
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger active = new AtomicInteger();
		AtomicInteger maximum = new AtomicInteger();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			int current = active.incrementAndGet();
			maximum.updateAndGet(previous -> Math.max(previous, current));
			entered.countDown();
			await(release);
			active.decrementAndGet();
			return response(chain, "{}");
		}).build();
		RemoteFeedPoller poller = new RemoteFeedPoller(client, new Gson(), IGNORE_ENTRIES, IGNORE_BOOLEAN,
			IGNORE_STATUS, IGNORE_BOOLEAN);

		poll(poller, "http://bridge.test/recruits");
		poll(poller, "http://bridge.test/recruits");

		assertTrue(entered.await(5, TimeUnit.SECONDS));
		release.countDown();
		assertEquals(1, maximum.get());
	}

	@Test
	public void cancelledGenerationCannotStartRequest()
		throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			requests.incrementAndGet();
			return response(chain, "{}");
		}).build();
		RemoteFeedPoller poller = new RemoteFeedPoller(client, new Gson(), IGNORE_ENTRIES, IGNORE_BOOLEAN,
			IGNORE_STATUS, IGNORE_BOOLEAN);
		long pollRequestGeneration = poller.pollRequestGeneration();

		poller.cancel();
		poller.poll("http://bridge.test/recruits", "secret", "Alice", pollRequestGeneration, 0);

		assertEquals(0, requests.get());
	}

	@Test
	public void cancelledResponseDoesNotPublishCallbacks()
		throws Exception
	{
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch callFinished = new CountDownLatch(1);
		CountDownLatch status = new CountDownLatch(1);
		AtomicInteger entries = new AtomicInteger();
		OkHttpClient client = new OkHttpClient.Builder().eventListener(new EventListener()
		{
			@Override
			public void callEnd(okhttp3.Call call)
			{
				callFinished.countDown();
			}

			@Override
			public void callFailed(okhttp3.Call call, IOException e)
			{
				callFinished.countDown();
			}
		}).addInterceptor(chain ->
		{
			entered.countDown();
			await(release);
			return response(chain, "{\"entries\":[]}");
		}).build();
		RemoteFeedPoller poller = new RemoteFeedPoller(client, new Gson(), values -> entries.incrementAndGet(),
			IGNORE_BOOLEAN, (lifecycle, value) -> status.countDown(), IGNORE_BOOLEAN);

		poll(poller, "http://bridge.test/recruits");
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		poller.cancel();
		release.countDown();

		assertTrue(callFinished.await(5, TimeUnit.SECONDS));
		assertFalse(status.await(0, TimeUnit.MILLISECONDS));
		assertEquals(0, entries.get());
	}

	@Test
	public void cancelWaitsForClaimedCallbackToFinishPublishing()
		throws Exception
	{
		CountDownLatch publicationStarted = new CountDownLatch(1);
		CountDownLatch releasePublication = new CountDownLatch(1);
		CountDownLatch publicationFinished = new CountDownLatch(1);
		CountDownLatch cancelReturned = new CountDownLatch(1);
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
			response(chain, "{\"entries\":[]}"))
			.build();
		RemoteFeedPoller poller = new RemoteFeedPoller(client, new Gson(), IGNORE_ENTRIES, IGNORE_BOOLEAN,
			(lifecycle, value) ->
		{
			publicationStarted.countDown();
			awaitUnchecked(releasePublication);
			publicationFinished.countDown();
		}, IGNORE_BOOLEAN);
		ExecutorService cancelExecutor = Executors.newSingleThreadExecutor();
		Future<?> cancellation = null;
		try
		{
			poll(poller, "http://bridge.test/recruits");
			assertTrue(publicationStarted.await(5, TimeUnit.SECONDS));
			long startingGeneration = poller.pollRequestGeneration();
			cancellation = cancelExecutor.submit(() ->
			{
				poller.cancel();
				cancelReturned.countDown();
			});

			awaitGenerationAdvance(poller, startingGeneration);
			assertEquals("cancel returned while a claimed callback was still publishing", 1,
				cancelReturned.getCount());
		}
		finally
		{
			releasePublication.countDown();
			if (cancellation != null)
			{
				cancellation.get(5, TimeUnit.SECONDS);
			}
			cancelExecutor.shutdownNow();
		}
		assertTrue(publicationFinished.await(5, TimeUnit.SECONDS));
		assertTrue(cancelReturned.await(5, TimeUnit.SECONDS));
	}

	@Test
	public void blankKeyHostActionDoesNotIssueRequest()
		throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		WeDoRaidsPlugin plugin = pluginForHostAction("", true, requests);

		invokeHost(plugin);

		assertEquals(0, requests.get());
	}

	@Test
	public void unverifiedHostActionDoesNotIssueRequest()
		throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		WeDoRaidsPlugin plugin = pluginForHostAction("secret", false, requests);

		invokeHost(plugin);

		assertEquals(0, requests.get());
	}

	private static WeDoRaidsPlugin pluginForHostAction(String key, boolean verified, AtomicInteger requests)
		throws Exception
	{
		WeDoRaidsPlugin plugin = new WeDoRaidsPlugin();
		WeDoRaidsConfig config = (WeDoRaidsConfig) Proxy.newProxyInstance(
			WeDoRaidsConfig.class.getClassLoader(), new Class<?>[]{WeDoRaidsConfig.class},
			(proxy, method, args) ->
			{
				if ("remoteFeedKey".equals(method.getName()))
				{
					return key;
				}
				if ("remoteFeedUrl".equals(method.getName()))
				{
					return "";
				}
				if (method.getReturnType() == boolean.class)
				{
					return false;
				}
				return null;
			});
		setField(plugin, "config", config);
		setField(plugin, "localPlayerName", "Alice");
		setField(plugin, "localVerified", verified);
		setField(plugin, "okHttpClient", new OkHttpClient.Builder().addInterceptor(chain ->
		{
			requests.incrementAndGet();
			return response(chain, "{\"ok\":true}");
		}).build());
		setField(plugin, "gson", new Gson());
		return plugin;
	}

	private static void poll(RemoteFeedPoller poller, String url)
	{
		poller.poll(url, "secret", "Alice", poller.pollRequestGeneration(), 0);
	}
	private static void invokeHost(WeDoRaidsPlugin plugin)
		throws Exception
	{
		Method hostRaid = WeDoRaidsPlugin.class.getDeclaredMethod("hostRaid", java.util.Map.class,
			java.util.function.Consumer.class);
		hostRaid.setAccessible(true);
		hostRaid.invoke(plugin, Collections.emptyMap(), (java.util.function.Consumer<String>) value ->
		{
		});
	}

	private static Response response(okhttp3.Interceptor.Chain chain, String body)
	{
		return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
			.message("OK").body(ResponseBody.create(MediaType.get("application/json"), body)).build();
	}

	private static void await(CountDownLatch latch)
		throws IOException
	{
		try
		{
			if (!latch.await(5, TimeUnit.SECONDS))
			{
				throw new IOException("Timed out waiting for test request");
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for test request", e);
		}
	}

	private static void awaitUnchecked(CountDownLatch latch)
	{
		try
		{
			if (!latch.await(5, TimeUnit.SECONDS))
			{
				throw new AssertionError("Timed out waiting for test barrier");
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError("Interrupted while waiting for test barrier", e);
		}
	}

	private static void awaitGenerationAdvance(RemoteFeedPoller poller, long startingGeneration)
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (poller.pollRequestGeneration() == startingGeneration)
		{
			if (System.nanoTime() >= deadline)
			{
				throw new AssertionError("Timed out waiting for cancel to advance poll request generation");
			}
			Thread.yield();
		}
	}
}
