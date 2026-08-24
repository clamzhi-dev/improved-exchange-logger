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

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.ScheduledExecutorService;
import static net.runelite.api.GrandExchangeOfferState.BUYING;
import static net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY;
import static net.runelite.api.GrandExchangeOfferState.CANCELLED_SELL;
import static net.runelite.api.GrandExchangeOfferState.EMPTY;
import static net.runelite.api.GrandExchangeOfferState.SELLING;
import static net.runelite.api.GrandExchangeOfferState.SOLD;

@Slf4j
public class ImprovedExchangeLoggerWriter
{
	// GE sale tax: 2% of the per-item price, rounded down, waived under 50gp/item,
	// capped at 5,000,000gp per item. offer.getSpent() reports the gross pre-tax
	// total on sells, not what the seller actually receives, so this is applied
	// after the fact using the average per-unit price (worth / qty).
	private static final int GE_TAX_MIN_PRICE = 50;
	private static final int GE_TAX_CAP_PER_ITEM = 5_000_000;

	private File logFile;
	private volatile boolean fileExist;

	private final int[] prevQuantity;
	private final int[] prevItemId;
	private final GrandExchangeOfferState[] prevState;

	private final ImprovedExchangeLoggerFormatting formatting;
	private final ScheduledExecutorService executor;
	private volatile ImprovedExchangeLoggerFormat format;
	private volatile boolean rewrite;
	private volatile boolean splitByAccount;
	private final String logPath;
	private final String logDir;
	private String activePath;
	private String logDate;

	ImprovedExchangeLoggerWriter(String path, ImprovedExchangeLoggerFormat form, boolean re, boolean split, ScheduledExecutorService executor)
	{
		fileExist = true;
		logDate = currentDateTime("yyyy-MM-dd");

		logPath = path;
		logDir = new File(path).getParent();
		format = form;
		rewrite = re;
		splitByAccount = split;
		this.executor = executor;

		prevQuantity = new int[8];
		prevItemId = new int[8];
		prevState = new GrandExchangeOfferState[8];
		Arrays.fill(prevQuantity, -1);          //Default to -1, because 0 is a valid state
		Arrays.fill(prevItemId, -1);

		formatting = new ImprovedExchangeLoggerFormatting();
		openLogFile(logPath);
	}

	// Called on the client thread. Snapshots the offer into plain data before handing
	// off to a background thread - GrandExchangeOffer must not be touched off-thread.
	public void grandExchangeEvent(GrandExchangeOfferChanged event, String accountName)
	{
		if (!fileExist)
		{
			return;
		}

		GrandExchangeOffer offer = event.getOffer();
		String[] split = currentDateTime("yyyy-MM-dd HH:mm:ss").split(" ", 2);

		ImprovedExchangeLoggerSlotStatus status = new ImprovedExchangeLoggerSlotStatus();
		status.date = split[0];
		status.time = split[1];
		status.state = offer.getState();
		status.slot = event.getSlot();
		status.item = offer.getItemId();
		status.qty = offer.getQuantitySold();
		status.worth = offer.getSpent();
		status.max = offer.getTotalQuantity();
		status.offer = offer.getPrice();

		if (formatting.anyEqualState(status.state, SELLING, SOLD, CANCELLED_SELL))
		{
			applySellTax(status);
		}

		executor.execute(() -> processEvent(status, accountName));
	}

	// Mutates worth (down to the net amount actually received) and sets tax (the amount
	// withheld) - tax stays 0 for buys and for sales under the 50gp/item exemption.
	private static void applySellTax(ImprovedExchangeLoggerSlotStatus status)
	{
		if (status.qty <= 0)
		{
			return;
		}

		int unitPrice = status.worth / status.qty;
		if (unitPrice < GE_TAX_MIN_PRICE)
		{
			return;
		}

		int unitTax = Math.min((unitPrice * 2) / 100, GE_TAX_CAP_PER_ITEM);
		status.tax = unitTax * status.qty;
		status.worth -= status.tax;
	}

	// Runs on a background thread. All disk I/O and mutable writer state lives here.
	private synchronized void processEvent(ImprovedExchangeLoggerSlotStatus status, String accountName)
	{
		if (!fileExist)
		{
			return;
		}

		String targetPath = computeLogPath(accountName);
		if (!targetPath.equals(activePath))    //Switch (or create) the file for this account
		{
			openLogFile(targetPath);
		}
		else if (!rewrite && !logDate.equals(status.date))  //New log if date changed during run-time
		{
			preserveCurrentFile(logDate);
		}

		if (duplicateHandler(status))         //Filter out duplicated events
		{
			return;
		}
		writeFile(status);
	}

	// The shared log, unless splitting by account is on and we know the account - then each
	// account gets its own file in the same directory.
	private String computeLogPath(String accountName)
	{
		if (!splitByAccount || accountName == null || accountName.isEmpty())
		{
			return logPath;
		}

		String sanitized = accountName.trim().replaceAll("[^a-zA-Z0-9]+", "_");
		if (sanitized.isEmpty())
		{
			return logPath;
		}

		return logDir + File.separator + "exchange_" + sanitized + ".log";
	}

	// Opens (or creates) the log file at path, applying the same rewrite/date-rollover
	// rules a plugin restart would apply - used both at startup and on an account switch.
	private void openLogFile(String path)
	{
		activePath = path;
		logFile = new File(path);

		if (logFile.isFile())
		{
			if (rewrite)
			{
				removeCurrentFile();			//If user only want one log file
				logFile = createLog(path);
			}
			else
			{
				fileDateCheck();				//Check if current log is for today's date
			}
		}
		else
		{
			logFile = createLog(path);       //First time running plugin (or first time for this account)
		}
	}

	private void writeFile(ImprovedExchangeLoggerSlotStatus status)
	{
		String writeLine;
		switch (format)
		{
			case TABULAR:
				writeLine = formatting.tabular(status);
				break;
			case JSON:
				writeLine = formatting.json(status);
				break;
			case TEXT:
			default:
				writeLine = formatting.plainText(status);
				break;
		}

		try (FileWriter writer = new FileWriter(logFile, true))
		{
			writer.write(writeLine + "\n");
		}
		catch (IOException e)
		{
			log.warn("An error occurred while writing to log file: " + e.toString());
		}
	}

	//GE OfferChanged events sometimes send duplicates of buying,selling and cancelled..
	//This method will compare current event with the previous.
	// 2 buying/selling events in sequence in the same slot can't have the same QuantitySold
	// 2 cancelled_buy/sell events in sequence in the same slot shouldn't be possible
	// Also requires the item id to match - otherwise a desync (e.g. a new offer placed with a
	// coincidentally equal quantity/state) would be wrongly swallowed as a duplicate.
	private boolean duplicateHandler(ImprovedExchangeLoggerSlotStatus status)
	{
		int slot = status.slot;
		boolean duplicate = false;
		boolean sameItem = prevItemId[slot] == status.item;

		if (sameItem
				&& ((prevQuantity[slot] == status.qty && formatting.anyEqualState(status.state, BUYING, SELLING))
					|| (prevState[slot] == status.state && formatting.anyEqualState(status.state, CANCELLED_BUY, CANCELLED_SELL))))
		{
			duplicate = true;
		}
		else    //EMPTY is always qty = 0, which makes next buy/sell assume it's a duplicate. Set it to -1
		{
			prevQuantity[slot] = ((status.state == EMPTY) ? -1 : status.qty);
			prevItemId[slot] = ((status.state == EMPTY) ? -1 : status.item);
			prevState[slot] = status.state;
		}
		return duplicate;
	}

	private String currentDateTime(String form)
	{
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat(form);   //"yyyy-MM-dd HH:mm:ss"
		return formatter.format(date);
	}

	//Adding _[fileDate] at the end of the current file name and creates a new log
	private void preserveCurrentFile(String fileDate)
	{
		String fileType = ".log";
		String rename = activePath.substring(0, activePath.length() - fileType.length());
		rename = rename + "_" + fileDate + fileType;

		if (!logFile.renameTo(new File(rename)))
		{
			log.debug("Failed to rename previous file to: " + rename);
		}
		logFile = createLog(activePath);
	}

	//on start: If the current log file does not have the current date, store it and create a new one
	private void fileDateCheck()
	{
		String fileDate = "";

		try
		{
			Scanner reader = new Scanner(logFile);	//Read current log´s date
			if (reader.hasNextLine())
			{
				fileDate = reader.nextLine();

				if (fileDate.contains("{"))		//Json format
				{
					String remove = "{\"date\":\"";
					fileDate = fileDate.substring(remove.length(), logDate.length() + remove.length());
				}
				else
				{
					fileDate = fileDate.substring(0, logDate.length());
				}
			}
			reader.close();
		}
		catch (IOException e)
		{
			log.warn("Couldn't read file: " + logFile.toString() + " " + e.toString());
		}

		if (!fileDate.equals(logDate) && !fileDate.equals(""))
		{
			preserveCurrentFile(fileDate);
		}
	}

	private File createLog(String path)
	{
		logDate = currentDateTime("yyyy-MM-dd");

		try
		{
			File log = new File(path);
			if (log.createNewFile())
			{
				fileExist = true;
				return log;
			}
		}
		catch (IOException e)
		{
			log.warn("An error occurred while creating a new log file" + e.toString());
		}

		fileExist = false;
		return null;
	}

	//Removes current logFile and creates a new one, used on startup if user only wants one log file
	public void removeCurrentFile()
	{
		try
		{
			if (!logFile.delete())
			{
				log.debug("Failed to delete old log file: " + logFile.toString());
			}
		}
		catch (Exception e)
		{
			log.warn("Error deleting old log file: " + e.toString());
		}
	}

	public void setRewrite(boolean re)
	{
		rewrite = re;
	}

	public void setFormat(ImprovedExchangeLoggerFormat form)
	{
		format = form;
	}

	public void setSplitByAccount(boolean split)
	{
		splitByAccount = split;
	}
}
