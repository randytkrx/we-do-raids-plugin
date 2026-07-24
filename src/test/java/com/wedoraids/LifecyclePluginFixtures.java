package com.wedoraids;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class LifecyclePluginFixtures
{
	private LifecyclePluginFixtures()
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
			void poll(String url, String key, String viewer, long pollRequestGeneration, long lifecycle)
			{
				if (pollRequestGeneration() == pollRequestGeneration)
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
	static final class RecordingNotificationPlugin extends WeDoRaidsPlugin
	{
		final AtomicInteger notificationCount = new AtomicInteger();

		@Override
		void notifyRecruit(RecruitEntry entry)
		{
			notificationCount.incrementAndGet();
		}
	}
}
