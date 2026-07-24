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
