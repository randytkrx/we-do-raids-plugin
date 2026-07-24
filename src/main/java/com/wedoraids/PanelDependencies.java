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
