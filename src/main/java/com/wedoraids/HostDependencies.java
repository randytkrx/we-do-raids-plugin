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
