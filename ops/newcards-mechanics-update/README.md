# ManaHub new cards and mechanics update

The daily Windows task runs at 06:00 in the Madrid system timezone. `StartWhenAvailable` catches a missed start when the configured interactive user session is available, and the scheduled task plus an exclusive file lock prevent overlap. It invokes the production Kotlin `:tools:tag-pipeline`; no classification rules are duplicated here.

Run `Test-MechanicsUpdate.ps1`, then `Install-ScheduledTask.ps1` from PowerShell. The service-role key stays in the existing ignored `local.properties` or the process environment. Do not put it in `config.json` or task arguments.

`state/manifest.jsonl` is the complete checkpoint. `pending.jsonl` survives a failed or interrupted upload and is retried on the next launch. A successful upload and read-back verification plus catalog publication precede an atomic full-manifest merge. The catalog command validates Scryfall keyword queries before activating new keys and writes a review report for unknown expressions. Run and catalog reports are written to `reports/` for every attempt. The Kotlin CLI fails rather than publish empty replacement data when Oracle Tags or another required enrichment source fails. After checkpoint promotion, cache cleanup keeps the two most recent versions of each Scryfall bulk file.
