package com.wedoraids;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

final class LifecyclePanelFixtures
{
	private static final HostFormPanel.HostActions NO_OP_ACTIONS = new HostFormPanel.HostActions()
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

	private static final HostDependencies HOST_DEFAULTS = new HostDependencies(NO_OP_ACTIONS, () -> 0, () -> null,
		() -> "Alice", raid -> -1, () ->
		{
		}, () ->
		{
		}, () -> false, world -> null);

	private static final PanelDependencies PANEL_DEFAULTS = new PanelDependencies((key, value) ->
	{
	},
		world ->
		{
		}, hub ->
		{
		}, () ->
		{
		});

	private LifecyclePanelFixtures()
	{
	}

	static WeDoRaidsPanel panel(WeDoRaidsConfig config)
	{
		return new WeDoRaidsPanel(config, HOST_DEFAULTS, PANEL_DEFAULTS);
	}

	static final class RecordingBridgePanel extends WeDoRaidsPanel
	{
		final List<BridgeStatus> statuses = new ArrayList<>();

		RecordingBridgePanel(WeDoRaidsConfig config)
		{
			super(config, HOST_DEFAULTS, PANEL_DEFAULTS);
		}

		@Override
		void setBridgeStatus(BridgeStatus status)
		{
			super.setBridgeStatus(status);
			statuses.add(status);
		}
	}
}
