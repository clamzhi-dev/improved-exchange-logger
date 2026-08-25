# Exchange Logger

A RuneLite plugin that writes every Grand Exchange transaction to a log file
on disk, in plain text, tabular (CSV), or JSON format.

## Where the logs go

By default, everything is written to:

```
%USERPROFILE%\.runelite\exchange-logger\exchange.log
```

If **One File** is off (the default), a new `exchange.log` is started each
day - the previous day's file is renamed to `exchange_<yyyy-MM-dd>.log`
before the new one is created, so you end up with one file per day plus the
current day's `exchange.log`. If **One File** is on, `exchange.log` is
cleared and rewritten fresh every time the plugin starts, so only the most
recent session's data is kept.

If **Split By Account** is on, each account gets its own file instead of a
shared one - `exchange_<AccountName>.log` (the display name, with anything
that isn't a letter or digit replaced by `_`) - and the One File / daily
rotation rules above apply independently to each account's file.

## Config options

| Option | Default | Description |
|---|---|---|
| **Log Format** | Plain text | Output format for each log line - Plain text, Tabular, or JSON (see below) |
| **One File** | Off | Rewrite `exchange.log` from scratch every session instead of rotating a new file each day |
| **Split By Account** | Off | Log each account to its own file instead of sharing `exchange.log` |

All three can be changed live from the plugin's config panel; changes take
effect on the next Grand Exchange event, no restart needed.

## Log formats

Each line represents one Grand Exchange offer-state change (offer placed,
partially filled, fully filled, cancelled, or slot cleared). `item` is the
item ID; `itemName` is its resolved display name, looked up live from the
client each event (so it's always correct for your game version, but is
empty for `EMPTY` slot-cleared lines, since there's no item to name).

`worth` is the actual gp that changed hands - what you paid on a buy, or
what you actually received on a sell, **after** the Grand Exchange's 2%
sale tax is deducted. `offer` is just the price per item you listed, which
on a sell isn't necessarily what it actually sold for. `tax` is the gp
withheld by the 2% tax (0 for buys, and for sales under the 50gp/item
exemption); `worth + tax` recovers the gross pre-tax sale value.

Below is a real buy-then-sell of a single item (bought for 153gp, then
listed to sell at 143gp but matched at 152gp, taxed 3gp, netting 149gp):

**Plain text** (default):
```
2026-08-24 23:44:10 state: BUY slot: 0 item: 2351 (Iron bar) max: 1 offer: 164
2026-08-24 23:44:11 state: BOUGHT slot: 0 item: 2351 (Iron bar) qty: 1 worth: 153 tax: 0
2026-08-24 23:44:35 state: SOLD slot: 0 item: 2351 (Iron bar) qty: 1 worth: 149 tax: 3
```

**Tabular** (CSV: `date,time,state,slot,item,qty,worth,max,offer,itemName,tax` -
`itemName` and `tax` are appended after the original columns rather than
interleaved, so existing column-position parsing keeps working; `itemName`
is quoted since it's the only free-text field):
```
2026-08-24,23:44:11,BOUGHT,0,2351,1,153,1,164,"Iron bar",0
2026-08-24,23:44:35,SOLD,0,2351,1,149,1,143,"Iron bar",3
```

**JSON** (one object per line):
```json
{"date":"2026-08-24","time":"23:44:35","state":"SOLD","slot":0,"item":2351,"qty":1,"worth":149,"max":1,"offer":143,"itemName":"Iron bar","tax":3}
```