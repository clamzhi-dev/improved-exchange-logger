/*
 * Copyright (c) 2021, Anton <https://github.com/istid>
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
package com.improvedexchangelogger;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@PluginDescriptor(
	name = "Improved Exchange Logger",
	description = "Stores all GE transactions in log file(s)",
	tags = {"grand exchange", "trade", "trading", "logger", "external"}
)
public class ImprovedExchangeLoggerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ImprovedExchangeLoggerConfig config;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ItemManager itemManager;

	private final String dirName = File.separator + "improved-exchange-logger";
	// No extension here - ImprovedExchangeLoggerWriter appends one matching the
	// selected log format (.log/.csv/.json).
	private final String logName = File.separator + "exchange";

	public static final String CONFIG_GROUP = "improvedexchangelogger";
	private ImprovedExchangeLoggerFormat format;
	private boolean rewrite;
	public String logPath;

	private ImprovedExchangeLoggerWriter writer;

	@Override
	protected void startUp() throws Exception
	{
		format = config.logFormat();
		rewrite = config.rewriteLog();

		String dir = RuneLite.RUNELITE_DIR.getPath() + dirName;
		new File(dir).mkdirs();
		logPath = dir + logName;

		writer = new ImprovedExchangeLoggerWriter(logPath, format, rewrite, config.splitByAccount(), executor);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("ImprovedExchangeLogger stopped!");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(CONFIG_GROUP))
		{
			if (event.getKey().equals("logFormat"))		//Change log file output format
			{
				format = config.logFormat();
				writer.setFormat(format);
			}
			else if (event.getKey().equals("rewriteLog"))	//Delete all old data when logging in. Only uses one log file
			{
				rewrite = config.rewriteLog();
				writer.setRewrite(rewrite);

			}
			else if (event.getKey().equals("splitByAccount"))	//Log each account to its own file
			{
				writer.setSplitByAccount(config.splitByAccount());
			}
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged offerEvent)
	{
		// Trades are cleared by the client during LOGIN_SCREEN/HOPPING/LOGGING_IN, ignore those
		if (client.getGameState() == GameState.LOGGED_IN && writer.isActive())
		{
			Player localPlayer = client.getLocalPlayer();
			String accountName = localPlayer != null ? localPlayer.getName() : null;

			int itemId = offerEvent.getOffer().getItemId();
			String itemName = itemId > 0 ? itemManager.getItemComposition(itemId).getName() : "";

			writer.grandExchangeEvent(offerEvent, accountName, itemName);
		}
	}

	@Provides
	ImprovedExchangeLoggerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ImprovedExchangeLoggerConfig.class);
	}
}
