# Simple (Hopefully) Finance Tracker (SFT)

Trying to build the most useful features. Lmk if you have ideas.

Uses Java25 and dependencies (pom.xml)

Notes:
- Some errors are silenced. Ask Claude if nescessary.


Project Status
- 7/4 did some work on UI. appears to be good. lots of AI gen
- 7/4 need to implement/refactor code from original sft project.
- 7/5 started refactor of holdings from 'finance-ingest' project
- 7/5 java port of finance-ingest project. Subcommands: init-db, sync, check, link. Link flow built, and "structuraly validated", but no real click through yet. Untested for now.


Design Decisions
- 7/5 - db now lives at data/database.db (gitignored), Pi copy is the source of truth, Mac reads a synced copy. When we add write-back features (allocation edits, what-if scenarios), replace this with a small Pi-side service that's the only process touching the file, instead of a network file mount or a heavier DB engine (Claude says this is the way).

For live data on PI:
- run finance-ingests sync on pi.
- point its finance_dbpath at pis local db ('sft_v2/data/database.db')
- add pi cron job for nightly sync.
- make holdingspage requery instead ob building once at startup - do when page becomes visible, or on a timer, not just once at boot


Todo:
settings interface / button / full screen toggle config
positions pie chart.
