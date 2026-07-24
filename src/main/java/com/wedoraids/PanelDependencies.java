package com.wedoraids;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class PanelDependencies
{
	private final BiConsumer<String, String> saveFilter;
	private final IntConsumer onHopWorld;
	private final Consumer<String> onJoinHub;
	private final Runnable onRefresh;

	PanelDependencies(BiConsumer<String, String> saveFilter, IntConsumer onHopWorld,
		Consumer<String> onJoinHub, Runnable onRefresh)
	{
		this.saveFilter = saveFilter;
		this.onHopWorld = onHopWorld;
		this.onJoinHub = onJoinHub;
		this.onRefresh = onRefresh;
	}

	BiConsumer<String, String> saveFilter()
	{
		return saveFilter;
	}

	IntConsumer onHopWorld()
	{
		return onHopWorld;
	}

	Consumer<String> onJoinHub()
	{
		return onJoinHub;
	}

	Runnable onRefresh()
	{
		return onRefresh;
	}
}
