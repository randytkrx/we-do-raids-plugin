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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns confirmed remote fields and operation sequencing; {@link HostFormPanel} owns displayed and draft maps.
 * Each lifecycle transition advances the token; stale callbacks fail {@code isCurrentOperation} and
 * {@code completeOperation} is a no-op.
 */
final class HostLiveState
{
	private Map<String, String> confirmedLiveFields;
	private boolean stopped;
	private boolean operationInFlight;
	private long operationSequence;
	private long activeOperationToken;

	void enterLivePost(Map<String, String> fields)
	{
		if (stopped)
		{
			return;
		}
		confirmedLiveFields = copy(fields);
		operationInFlight = false;
		activeOperationToken = ++operationSequence;
	}

	void exitLivePost()
	{
		confirmedLiveFields = null;
		operationInFlight = false;
		activeOperationToken = ++operationSequence;
	}

	boolean canStartOperation()
	{
		return !stopped && !operationInFlight;
	}

	long beginOperation()
	{
		if (!canStartOperation())
		{
			return -1;
		}
		operationInFlight = true;
		activeOperationToken = ++operationSequence;
		return activeOperationToken;
	}

	boolean isCurrentOperation(long operation)
	{
		return !stopped && operationInFlight && activeOperationToken == operation;
	}

	/** Only the active token can complete; stale completions are no-ops. */
	void completeOperation(long operation)
	{
		if (isCurrentOperation(operation))
		{
			operationInFlight = false;
		}
	}

	void stop()
	{
		stopped = true;
		operationInFlight = false;
		activeOperationToken = ++operationSequence;
	}

	boolean isStopped()
	{
		return stopped;
	}

	Map<String, String> confirmedFieldsSnapshot()
	{
		return confirmedLiveFields == null ? null : copy(confirmedLiveFields);
	}

	void confirmLiveFields(Map<String, String> fields)
	{
		if (!stopped)
		{
			confirmedLiveFields = copy(fields);
		}
	}

	private static Map<String, String> copy(Map<String, String> fields)
	{
		return fields == null ? null : new LinkedHashMap<>(fields);
	}
}
