package com.wedoraids;

import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

final class HostDependencies
{
	private final HostFormPanel.HostActions actions;
	private final IntSupplier currentWorld;
	private final Supplier<String> coxLayout;
	private final Supplier<String> localIgn;
	private final ToIntFunction<RaidType> userKc;
	private final Runnable requestKc;
	private final Runnable onIdleWarn;
	private final BooleanSupplier autoHub;
	private final IntFunction<String> worldBlockReason;

	HostDependencies(HostFormPanel.HostActions actions, IntSupplier currentWorld,
		Supplier<String> coxLayout, Supplier<String> localIgn, ToIntFunction<RaidType> userKc,
		Runnable requestKc, Runnable onIdleWarn, BooleanSupplier autoHub,
		IntFunction<String> worldBlockReason)
	{
		this.actions = actions;
		this.currentWorld = currentWorld;
		this.coxLayout = coxLayout;
		this.localIgn = localIgn;
		this.userKc = userKc;
		this.requestKc = requestKc;
		this.onIdleWarn = onIdleWarn;
		this.autoHub = autoHub;
		this.worldBlockReason = worldBlockReason;
	}

	HostFormPanel.HostActions actions()
	{
		return actions;
	}

	IntSupplier currentWorld()
	{
		return currentWorld;
	}

	Supplier<String> coxLayout()
	{
		return coxLayout;
	}

	Supplier<String> localIgn()
	{
		return localIgn;
	}

	ToIntFunction<RaidType> userKc()
	{
		return userKc;
	}

	Runnable requestKc()
	{
		return requestKc;
	}

	Runnable onIdleWarn()
	{
		return onIdleWarn;
	}

	BooleanSupplier autoHub()
	{
		return autoHub;
	}

	IntFunction<String> worldBlockReason()
	{
		return worldBlockReason;
	}
}
