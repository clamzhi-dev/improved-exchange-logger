package com.improvedexchangelogger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ImprovedExchangeLoggerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ImprovedExchangeLoggerPlugin.class);
		RuneLite.main(args);
	}
}