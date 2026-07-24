package com.wedoraids;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

final class LifecyclePanelFixtures
{
	private LifecyclePanelFixtures()
	{
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
		final HostDependencies hostDependencies = new HostDependencies(actions, () -> 0, () -> null,
			() -> "Alice", raid -> -1, () ->
			{
			}, () ->
			{
			}, () -> false, world -> null);
		final PanelDependencies panelDependencies = new PanelDependencies((key, value) ->
		{
		},
			world ->
			{
			}, hub ->
			{
			}, () ->
			{
			});
		return new WeDoRaidsPanel(config, hostDependencies, panelDependencies);
	}
	static final class RecordingBridgePanel extends WeDoRaidsPanel
	{
		final List<BridgeStatus> statuses = new ArrayList<>();

		RecordingBridgePanel(WeDoRaidsConfig config)
		{
			super(config,
				new HostDependencies(new HostFormPanel.HostActions()
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
				}, () -> 0, () -> null, () -> "Alice", raid -> -1, () ->
				{
				}, () ->
				{
				}, () -> false,
					world -> null),
				new PanelDependencies((key, value) ->
				{
				}, world ->
				{
				}, hub ->
				{
				}, () ->
				{
				}));
		}

		@Override
		void setBridgeStatus(BridgeStatus status)
		{
			super.setBridgeStatus(status);
			statuses.add(status);
		}
	}
}
