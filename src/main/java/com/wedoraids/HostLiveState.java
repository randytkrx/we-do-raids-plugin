package com.wedoraids;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns confirmed remote fields and operation sequencing; {@link HostFormPanel} owns displayed and draft maps.
 * Each lifecycle transition advances the token; stale callbacks fail {@code accepts} and {@code complete} is a no-op.
 */
final class HostLiveState
{
	private Map<String, String> confirmedFields;
	private boolean stopped;
	private boolean inFlight;
	private long operationSequence;
	private long activeOperation;

	void enter(Map<String, String> fields)
	{
		if (stopped)
		{
			return;
		}
		confirmedFields = copy(fields);
		inFlight = false;
		activeOperation = ++operationSequence;
	}

	void exit()
	{
		confirmedFields = null;
		inFlight = false;
		activeOperation = ++operationSequence;
	}

	boolean canStart()
	{
		return !stopped && !inFlight;
	}

	long begin()
	{
		if (!canStart())
		{
			return -1;
		}
		inFlight = true;
		activeOperation = ++operationSequence;
		return activeOperation;
	}

	boolean accepts(long operation)
	{
		return !stopped && inFlight && activeOperation == operation;
	}

	/** Only the active token can complete; stale completions are no-ops. */
	void complete(long operation)
	{
		if (accepts(operation))
		{
			inFlight = false;
		}
	}

	void stop()
	{
		stopped = true;
		inFlight = false;
		activeOperation = ++operationSequence;
	}

	boolean isStopped()
	{
		return stopped;
	}

	Map<String, String> confirmedSnapshot()
	{
		return confirmedFields == null ? null : copy(confirmedFields);
	}

	void confirm(Map<String, String> fields)
	{
		if (!stopped)
		{
			confirmedFields = copy(fields);
		}
	}

	private static Map<String, String> copy(Map<String, String> fields)
	{
		return fields == null ? null : new LinkedHashMap<>(fields);
	}
}
