package com.wedoraids;

import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

final class LifecycleTestSupport
{
	private LifecycleTestSupport()
	{
	}

	static WeDoRaidsPlugin plugin(ScheduledExecutorService executor, String key, boolean demo,
		CountDownLatch polled)
		throws Exception
	{
		return plugin(executor, config(key, demo), polled);
	}

	static WeDoRaidsPlugin plugin(ScheduledExecutorService executor, WeDoRaidsConfig config,
		CountDownLatch polled)
		throws Exception
	{
		WeDoRaidsPlugin plugin = new WeDoRaidsPlugin();
		setField(plugin, "config", config);
		setField(plugin, "executor", executor);
		RemoteFeedPoller poller = new RemoteFeedPoller(new okhttp3.OkHttpClient(), new Gson(), values -> { },
			values -> { }, (lifecycle, value) -> { }, value -> { })
		{
			@Override
			void poll(String url, String key, String viewer, long epoch, long lifecycle)
			{
				if (pollEpoch() == epoch)
				{
					polled.countDown();
				}
			}
		};
		setField(plugin, "remoteFeedPoller", poller);
		return plugin;
	}

	static WeDoRaidsConfig config(String key, boolean demo)
	{
		return (WeDoRaidsConfig) Proxy.newProxyInstance(
			WeDoRaidsConfig.class.getClassLoader(), new Class<?>[]{WeDoRaidsConfig.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "remoteFeedKey":
						return key;
					case "remoteFeedUrl":
						return "http://bridge.test/recruits";
					case "demoData":
						return demo;
					default:
						if (method.getReturnType() == boolean.class)
						{
							return false;
						}
						if (method.getReturnType() == int.class)
						{
							return 0;
						}
						if (method.getReturnType() == String.class)
						{
							return "";
						}
						return null;
				}
			});
	}

	static WeDoRaidsConfig mutableConfig(AtomicReference<String> key, AtomicBoolean demo)
	{
		return (WeDoRaidsConfig) Proxy.newProxyInstance(
			WeDoRaidsConfig.class.getClassLoader(), new Class<?>[]{WeDoRaidsConfig.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "remoteFeedKey":
						return key.get();
					case "remoteFeedUrl":
						return "http://bridge.test/recruits";
					case "demoData":
						return demo.get();
					default:
						if (method.getReturnType() == boolean.class)
						{
							return false;
						}
						if (method.getReturnType() == int.class)
						{
							return 0;
						}
						if (method.getReturnType() == String.class)
						{
							return "";
						}
						return null;
				}
			});
	}

	static WeDoRaidsConfig feedConfig()
	{
		return (WeDoRaidsConfig) Proxy.newProxyInstance(
			WeDoRaidsConfig.class.getClassLoader(), new Class<?>[]{WeDoRaidsConfig.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "showTob":
					case "showCox":
					case "showToa":
						return true;
					case "tierFilter":
					case "keywordFilter":
						return "";
					case "demoData":
						return false;
					default:
						return method.getDefaultValue();
				}
			});
	}

	static WeDoRaidsPanel panel(WeDoRaidsConfig config)
	{
		HostFormPanel.HostActions actions = new HostFormPanel.HostActions()
		{
			@Override
			public void submit(Map<String, String> fields, java.util.function.Consumer<String> status)
			{
			}

			@Override
			public void update(Map<String, String> fields, java.util.function.Consumer<String> status)
			{
			}

			@Override
			public void close(Map<String, String> fields, java.util.function.Consumer<String> status)
			{
			}
		};
		return new WeDoRaidsPanel(config, actions, (key, value) -> { }, world -> { }, () -> 0,
			() -> null, () -> "Alice", raid -> -1, () -> { }, () -> { }, () -> false, hub -> { },
			() -> { }, world -> null);
	}

	static void invoke(Object target, String name)
		throws Exception
	{
		Method method = declaredMethod(target.getClass(), name);
		method.setAccessible(true);
		method.invoke(target);
	}

	static void invoke(Object target, String name, Object argument)
		throws Exception
	{
		Method method = declaredMethod(target.getClass(), name, List.class);
		method.setAccessible(true);
		method.invoke(target, argument);
	}

	static void invoke(Object target, String name, int argument)
		throws Exception
	{
		Method method = declaredMethod(target.getClass(), name, int.class);
		method.setAccessible(true);
		method.invoke(target, argument);
	}

	static void invokeBridgeStatus(WeDoRaidsPlugin plugin, BridgeStatus status, long lifecycle)
		throws Exception
	{
		Method method = declaredMethod(WeDoRaidsPlugin.class, "setBridgeStatus", BridgeStatus.class, long.class);
		method.setAccessible(true);
		method.invoke(plugin, status, lifecycle);
	}

	static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes)
		throws NoSuchMethodException
	{
		for (Class<?> current = type; current != null; current = current.getSuperclass())
		{
			try
			{
				return current.getDeclaredMethod(name, parameterTypes);
			}
			catch (NoSuchMethodException ignored)
			{
			}
		}
		throw new NoSuchMethodException(name);
	}

	static Object field(Object target, String name)
		throws Exception
	{
		Field field = declaredField(target.getClass(), name);
		field.setAccessible(true);
		return field.get(target);
	}

	static void setField(Object target, String name, Object value)
		throws Exception
	{
		Field field = declaredField(target.getClass(), name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Field declaredField(Class<?> type, String name)
		throws NoSuchFieldException
	{
		for (Class<?> current = type; current != null; current = current.getSuperclass())
		{
			try
			{
				return current.getDeclaredField(name);
			}
			catch (NoSuchFieldException ignored)
			{
			}
		}
		throw new NoSuchFieldException(name);
	}

	static World world(int id, EnumSet<WorldType> types)
	{
		return World.builder().id(id).types(types).address("oldschool" + id + ".runescape.com")
			.activity("").location(0).players(0).build();
	}

	static WorldResult worlds(World... values)
	{
		WorldResult result = new WorldResult();
		result.setWorlds(new ArrayList<>(Arrays.asList(values)));
		return result;
	}

	static void awaitIgnoringInterrupt(CountDownLatch latch)
	{
		boolean interrupted = false;
		while (true)
		{
			try
			{
				latch.await();
				break;
			}
			catch (InterruptedException ignored)
			{
				interrupted = true;
			}
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	static Response blockingResponse(okhttp3.Interceptor.Chain chain, String body,
		CountDownLatch readStarted, CountDownLatch releaseRead, CountDownLatch bodyClosed)
	{
		Buffer bodyBuffer = new Buffer().writeUtf8(body);
		BufferedSource source = Okio.buffer(new ForwardingSource(bodyBuffer)
		{
			private boolean firstRead = true;

			@Override
			public long read(Buffer sink, long byteCount) throws IOException
			{
				if (firstRead)
				{
					firstRead = false;
					readStarted.countDown();
					await(releaseRead);
				}
				return super.read(sink, byteCount);
			}
		});
		ResponseBody responseBody = new ResponseBody()
		{
			@Override
			public MediaType contentType()
			{
				return MediaType.get("application/json");
			}

			@Override
			public long contentLength()
			{
				return body.length();
			}

			@Override
			public BufferedSource source()
			{
				return source;
			}

			@Override
			public void close()
			{
				try
				{
					super.close();
				}
				finally
				{
					bodyClosed.countDown();
				}
			}
		};
		return new Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200)
			.message("OK").body(responseBody).build();
	}

	static void await(CountDownLatch latch)
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

	static class BlockingWorldPlugin extends WeDoRaidsPlugin
	{
		final CountDownLatch loadStarted = new CountDownLatch(1);
		final CountDownLatch releaseLoad = new CountDownLatch(1);
		final AtomicReference<Thread> loaderThread = new AtomicReference<>();

		@Override
		WorldResult loadWorlds()
		{
			loaderThread.set(Thread.currentThread());
			loadStarted.countDown();
			awaitIgnoringInterrupt(releaseLoad);
			return null;
		}
	}

	static final class CompletedWorldPlugin extends WeDoRaidsPlugin
	{
		final CountDownLatch loadCompleted = new CountDownLatch(1);

		@Override
		WorldResult loadWorlds()
		{
			loadCompleted.countDown();
			return worlds(world(301, EnumSet.noneOf(WorldType.class)));
		}
	}

	static final class RestartingWorldPlugin extends WeDoRaidsPlugin
	{
		private final AtomicInteger loadCount = new AtomicInteger();
		final CountDownLatch firstLoadStarted = new CountDownLatch(1);
		final CountDownLatch secondLoadStarted = new CountDownLatch(1);
		final CountDownLatch releaseFirstLoad = new CountDownLatch(1);
		final CountDownLatch releaseSecondLoad = new CountDownLatch(1);
		final AtomicReference<Thread> firstLoaderThread = new AtomicReference<>();

		@Override
		WorldResult loadWorlds()
		{
			if (loadCount.incrementAndGet() == 1)
			{
				firstLoaderThread.set(Thread.currentThread());
				firstLoadStarted.countDown();
				awaitIgnoringInterrupt(releaseFirstLoad);
				return worlds(world(301, EnumSet.of(WorldType.PVP)));
			}
			secondLoadStarted.countDown();
			awaitIgnoringInterrupt(releaseSecondLoad);
			return null;
		}
	}

	static final class RecordingNotificationPlugin extends WeDoRaidsPlugin
	{
		final AtomicInteger notificationCount = new AtomicInteger();

		@Override
		void notifyRecruit(RecruitEntry entry)
		{
			notificationCount.incrementAndGet();
		}
	}

	static final class RecordingBridgePanel extends WeDoRaidsPanel
	{
		final List<BridgeStatus> statuses = new ArrayList<>();

		RecordingBridgePanel(WeDoRaidsConfig config)
		{
			super(config, new HostFormPanel.HostActions()
			{
				@Override
				public void submit(Map<String, String> fields, java.util.function.Consumer<String> status)
				{
				}

				@Override
				public void update(Map<String, String> fields, java.util.function.Consumer<String> status)
				{
				}

				@Override
				public void close(Map<String, String> fields, java.util.function.Consumer<String> status)
				{
				}
			}, (key, value) -> { }, world -> { }, () -> 0, () -> null, () -> "Alice", raid -> -1,
				() -> { }, () -> { }, () -> false, hub -> { }, () -> { }, world -> null);
		}

		@Override
		void setBridgeStatus(BridgeStatus status)
		{
			super.setBridgeStatus(status);
			statuses.add(status);
		}
	}

	static final class RecordingClientThread extends ClientThread
	{
		final AtomicInteger invokeCount = new AtomicInteger();

		@Override
		public void invoke(Runnable runnable)
		{
			invokeCount.incrementAndGet();
		}
	}

	static final class CapturingScheduledExecutor extends ScheduledThreadPoolExecutor
	{
		private final List<Runnable> tasks = new ArrayList<>();
		private final List<CapturedScheduledFuture> futures = new ArrayList<>();
		private final CountDownLatch blockedSchedules;
		private final CountDownLatch releaseSchedules;

		CapturingScheduledExecutor()
		{
			this(0);
		}

		CapturingScheduledExecutor(int schedulesToBlock)
		{
			super(1);
			blockedSchedules = new CountDownLatch(schedulesToBlock);
			releaseSchedules = new CountDownLatch(schedulesToBlock == 0 ? 0 : 1);
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
			TimeUnit unit)
		{
			CapturedScheduledFuture future = new CapturedScheduledFuture();
			synchronized (this)
			{
				tasks.add(command);
				futures.add(future);
			}
			blockedSchedules.countDown();
			await(releaseSchedules);
			return future;
		}

		synchronized int taskCount()
		{
			return tasks.size();
		}

		synchronized CapturedScheduledFuture future(int index)
		{
			return futures.get(index);
		}

		synchronized void runTask(int index)
		{
			tasks.get(index).run();
		}

		void awaitBlockedSchedules()
		{
			await(blockedSchedules);
		}

		void releaseSchedules()
		{
			releaseSchedules.countDown();
		}

		synchronized int uncancelledFutureCount()
		{
			int count = 0;
			for (CapturedScheduledFuture future : futures)
			{
				if (!future.isCancelled())
				{
					count++;
				}
			}
			return count;
		}

		synchronized void cancelAll()
		{
			for (CapturedScheduledFuture future : futures)
			{
				future.cancel(false);
			}
		}
	}

	static final class ImmediateScheduledExecutor extends ScheduledThreadPoolExecutor
	{
		ImmediateScheduledExecutor()
		{
			super(1);
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
			TimeUnit unit)
		{
			command.run();
			return new CapturedScheduledFuture();
		}
	}

	static final class CapturedScheduledFuture implements ScheduledFuture<Object>
	{
		private volatile boolean cancelled;

		@Override
		public long getDelay(TimeUnit unit)
		{
			return 0;
		}

		@Override
		public int compareTo(Delayed other)
		{
			return 0;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			cancelled = true;
			return true;
		}

		@Override
		public boolean isCancelled()
		{
			return cancelled;
		}

		@Override
		public boolean isDone()
		{
			return cancelled;
		}

		@Override
		public Object get() throws InterruptedException, ExecutionException
		{
			return null;
		}

		@Override
		public Object get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException
		{
			return null;
		}
	}
}
