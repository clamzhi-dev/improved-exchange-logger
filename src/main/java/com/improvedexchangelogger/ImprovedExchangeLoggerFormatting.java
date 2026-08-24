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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.GrandExchangeOfferState;
import static net.runelite.api.GrandExchangeOfferState.*;

public class ImprovedExchangeLoggerFormatting
{
	// Stateless; reused across the JSON formatter to avoid rebuilding it per call.
	private static final Gson GSON = new GsonBuilder().create();

	public String plainText(ImprovedExchangeLoggerSlotStatus status)
	{
		String time = status.date + " " + status.time;
		String line;

		//First offer for item
		if (status.qty == 0 && anyEqualState(status.state, BUYING, SELLING))
		{
			//Differentiate the first offer state from subsequent ones
			String firstState = ((status.state == BUYING) ? "BUY" : "SELL");

			line = (time + " state: " + firstState + " slot: " + status.slot + " item: " + status.item
					+ " max: " + status.max + " offer: " + status.offer);
		}
		else if (anyEqualState(status.state, CANCELLED_BUY, CANCELLED_SELL))
		{
			line = (time + " state: " + status.state + " slot: " + status.slot + " item: " + status.item
					+ " qty: " + status.qty + " worth: " + status.worth + " tax: " + status.tax + " max: " + status.max);
		}
		else if (status.state == EMPTY)
		{
			line = (time + " state: " + status.state + " slot: " + status.slot);
		}
		else
		{
			line = (time + " state: " + status.state + " slot: " + status.slot + " item: " + status.item
					+ " qty: " + status.qty + " worth: " + status.worth + " tax: " + status.tax);
		}
		return line;
	}

	public String tabular(ImprovedExchangeLoggerSlotStatus status)
	{
		return (status.date + "," + status.time + "," + status.state
				+ "," + status.slot + "," + status.item + "," + status.qty
				+ "," + status.worth + "," + status.max + "," + status.offer + "," + status.tax);
	}

	public String json(ImprovedExchangeLoggerSlotStatus status)
	{
		return GSON.toJson(status);
	}

	public boolean anyEqualState(GrandExchangeOfferState expected, GrandExchangeOfferState ...array)
	{
		for (GrandExchangeOfferState state : array)
		{
			if (state.equals(expected))
			{
				return true;
			}
		}
		return false;
	}
}
