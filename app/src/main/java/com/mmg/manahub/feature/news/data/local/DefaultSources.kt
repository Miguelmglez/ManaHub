package com.mmg.manahub.feature.news.data.local

import com.mmg.manahub.core.data.local.entity.ContentSourceEntity

object DefaultSources {

    // ── English article sources ───────────────────────────────────────────────

    val articles = listOf(
        ContentSourceEntity(
            id = "default_article_mtggoldfish",
            name = "MTGGoldfish",
            feedUrl = "https://www.mtggoldfish.com/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_article_scg",
            name = "Star City Games",
            feedUrl = "https://articles.starcitygames.com/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_article_cardkingdom",
            name = "Card Kingdom Blog",
            feedUrl = "https://blog.cardkingdom.com/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_article_draftsim",
            name = "Draftsim",
            feedUrl = "https://draftsim.com/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_article_mtgazone",
            name = "MTG Arena Zone",
            feedUrl = "https://mtgazone.com/news/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_article_mtgrocks",
            name = "MTG Rocks",
            feedUrl = "https://mtgrocks.com/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        // Verified 2026-07-14 via curl (HTTP 200 + valid RSS + item count) — News feature
        // improvements Phase 7.
        ContentSourceEntity(
            id = "default_article_hipsters",
            name = "Hipsters of the Coast",
            // Trailing slash is the final redirect target — `/feed` alone 301s here.
            feedUrl = "https://www.hipstersofthecoast.com/feed/",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_article_commandersherald",
            name = "Commander's Herald",
            // Trailing slash is the final redirect target — `/feed` alone 301s here.
            feedUrl = "https://commandersherald.com/feed/",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_article_quietspeculation",
            name = "Quiet Speculation",
            feedUrl = "https://www.quietspeculation.com/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_article_wargamer",
            name = "Wargamer",
            // MTG-scoped tag feed, not the general Wargamer feed — confirmed all items are
            // MTG-relevant.
            feedUrl = "https://www.wargamer.com/magic-the-gathering/feed",
            type = "ARTICLE",
            isDefault = true,
            language = "en",
        ),
        // Rejected 2026-07-14 (News feature improvements Phase 7) — no entity added:
        //   Bleeding Cool MTG    — redirects to a 404 `path.php`, dead feed url.
        //   Dot Esports MTG      — 403, Cloudflare JS challenge, unfetchable.
        //   MTGStocks news       — no RSS; every candidate path returns 202 (challenge/queue
        //                          response, not a feed).
        //   TCGplayer Infinite   — no public RSS; `/feed` returns the SPA HTML shell.
        //
        // Retired 2026-07-16 (dead-source cleanup, confirmed via direct curl checks — see
        // NewsRepositoryImpl.reconcileDefaultSources, which deletes these ids from installs that
        // already seeded them under the old catalog):
        //   EDHREC (default_article_edhrec)             — https://edhrec.com/articles/feed TCP
        //                                                  connects but hangs/times out (15s+, 0
        //                                                  bytes); Cloudflare bot-protection
        //                                                  black-holing non-browser clients, not a
        //                                                  transient blip.
        //   GatheringMagic (default_article_gatheringmagic) — https://www.gatheringmagic.com/feed/
        //                                                  connection refused on 443; the host's
        //                                                  server is down.
        //   Cranial Insertion (default_article_cranial) — https://cranial-insertion.com/feed
        //                                                  redirects (301→http, 302→https) then
        //                                                  404s; feed removed.
    )

    // ── Spanish article sources ───────────────────────────────────────────────
    // Verified: MagicBlogTK (Blogspot feed confirmed valid, last post March 2026)
    // Attempted but failed: WotC España (404), La Caverna de Voltir (SSL error)
    //
    // Re-attempted 2026-07-14 (News feature improvements Phase 7) — NONE verified, no entity
    // added:
    //   WotC España        — site is now a Nuxt SPA; `/es/rss/rss.xml` and `/es/news/rss` both
    //                        404, `/es/news` only exposes `hreflang` alternates, no RSS
    //                        `<link rel="alternate" type="application/rss+xml">` anywhere on the
    //                        site. The EN counterpart (`default_article_wizards`,
    //                        `https://magic.wizards.com/en/rss/rss.xml`) 404s the same way and was
    //                        removed from `articles` above — WotC has no RSS feed at all anymore.
    //   Vandal MTG topic   — no working RSS endpoint found (404/connection failure on every path
    //                        tried).
    //   mtgdecks.net       — Cloudflare-blocked (403 "Just a moment" challenge) on every path.
    //   ElDesmarque        — 403 Access Denied.

    val articlesEs = listOf(
        ContentSourceEntity(
            id = "default_article_magicblogtk_es",
            name = "MagicBlogTK",
            feedUrl = "https://www.magicblogtk.com/feeds/posts/default?alt=rss",
            type = "ARTICLE",
            isDefault = true,
            language = "es",
        ),
    )

    // ── German article sources ────────────────────────────────────────────────
    // Note: WotC DE (404), Three for One Trading (404) — no DE article feeds verified.
    // articlesDE is empty until a valid feed is confirmed.

    val articlesDe = emptyList<ContentSourceEntity>()

    // ── English video sources ─────────────────────────────────────────────────
    // channel_id corrections 2026-07-16 (dead-source cleanup, confirmed via direct curl checks —
    // the OLD ids below returned a genuine Google-served 404 "requested URL not found on this
    // server"; the corrected ids return HTTP 200 with the right <title> and 15 <entry> items).
    // See NewsRepositoryImpl.reconcileDefaultSources, which rewrites feedUrl + resets the
    // etag/lastModified/lastFetchedAt watermark for installs that already seeded the old id.

    val videos = listOf(
        ContentSourceEntity(
            id = "default_video_mtg_official",
            name = "Magic: The Gathering",
            // OLD (dead): channel_id=UCpwK0zGsMU0C9V_gPMNngSw
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC8ZGymAvfP97qJabgqUkz4A",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_command_zone",
            name = "The Command Zone",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCLsiaNUb42gRAP7ewbJ0ecQ",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_tolarian",
            name = "Tolarian Community College",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC7-hR5EfgpM6oHfiGDkxfMA",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_mtggoldfish",
            name = "MTGGoldfish",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCVOzdxodlqNC2Y3KSvVQiVA",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_rhystic",
            name = "Rhystic Studies",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC8e0Sg8TmRRFJytjEGhmVTg",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_nitpicking",
            // Feed title is now "Nitpicking Nerd" (singular) — same channel, just a display-name
            // change upstream; keep our own `name` field as-is.
            name = "Nitpicking Nerds",
            // OLD (dead): channel_id=UCiIYx9sFBPjq1P8VGkHDALQ
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCrLsxBysUHnpSKRpXMbMVzg",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_good_morning",
            name = "Good Morning Magic",
            // OLD (dead): channel_id=UCvE8Mza7uRuIIqmMLGsz01g — note this is only a casing/
            // character difference from the corrected id below, easy to mistype.
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCvE8Mza7uRuiYlwiSDyJi9A",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_pleasant_kenobi",
            name = "Pleasant Kenobi",
            // OLD (dead): channel_id=UCkUELeIMduQbsv8MhmMEPpg
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC_b074DeDtbHukufPf2O1kw",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_legenvd",
            name = "LegenVD",
            // OLD (dead): channel_id=UCd0kth9C1hqSiaqoQ9TINaA
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCd0kth9C1hqJiaoedeBZ0cQ",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_loadingreadyrun",
            name = "LoadingReadyRun",
            // OLD (dead): channel_id=UCLBNH4hp-NaMcqc5M9MYqzA
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCwjN2uVdL9A0i3gaIHKFzuA",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        // Verified 2026-07-14 (News feature improvements Phase 7) — 15 entries each, all active.
        ContentSourceEntity(
            id = "default_video_playtowin",
            name = "Play to Win",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC7339iJMCETmek3jdx9LOkg",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_covertgoblue",
            name = "CovertGoBlue",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC-UZjHl2kZ-6XKBLgbFgGAQ",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
        ContentSourceEntity(
            id = "default_video_cardmarket_magic",
            name = "Cardmarket - Magic",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCwatLpmoIeoL9egqVW2Cxbw",
            type = "VIDEO",
            isDefault = true,
            language = "en",
        ),
    )

    // ── Spanish video sources ─────────────────────────────────────────────────
    // Verified: Rebellion MTG (UCaWvebWif9HQblQlrgte7YA — confirmed valid Atom feed, active)
    //           MagicBlogTK / Magic Arena MundoTK (UC6LNy6IqI76s7emSw0zCZ2g — confirmed valid)
    // Attempted but not verified: @wizards_magicES, @Nsjavier, @ElCubilDelJabalí
    //
    // Re-attempted 2026-07-14 (News feature improvements Phase 7) — 1 new source verified, the
    // rest re-checked and rejected:
    //   @CommanderBCN     — resolves to UCWhjLOQcVzWrFd3q1HeLriA ("commanderbcn"), but the feed
    //                       has only 3 entries, all from 2007-2009, unrelated personal/travel
    //                       content — handle appears squatted/repurposed, not the MTG channel.
    //   @TKMagicBlog      — resolves to UC6LNy6IqI76s7emSw0zCZ2g, the SAME channel already
    //                       seeded as `default_video_magicblogtk_es` — confirmed duplicate.
    //   @Nsjavier         — resolves to UCmk6QaIdjjVIqAXM8uEDqOg ("Nils Styf"), 0 video entries.
    //   @ElCubilDelJabali, @wizards_magicES — handle pages return HTTP 404, could not resolve.
    //   @Duelistas        — UCFreg9Hox-yZlYQmtz5HrDw, active but Portuguese-language general
    //                       gaming content, not MTG, not Spanish.
    //   @MagicParaTodos   — UCblryjlIvD9B_dNmTZtQtpg, MTG content but Portuguese-language, last
    //                       activity 2023 (wrong language AND stale).

    val videosEs = listOf(
        ContentSourceEntity(
            id = "default_video_rebellion_mtg_es",
            name = "Rebellion MTG",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCaWvebWif9HQblQlrgte7YA",
            type = "VIDEO",
            isDefault = true,
            language = "es",
        ),
        ContentSourceEntity(
            id = "default_video_magicblogtk_es",
            name = "Magic Arena MundoTK",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC6LNy6IqI76s7emSw0zCZ2g",
            type = "VIDEO",
            isDefault = true,
            language = "es",
        ),
        // Verified 2026-07-14: 15 entries, cEDH deck-tech content, uploads multiple times daily.
        ContentSourceEntity(
            id = "default_video_lacasadelcomandante_es",
            name = "La Casa del Comandante",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCQsXkyjcL1gck9zaHgzBFLA",
            type = "VIDEO",
            isDefault = true,
            language = "es",
        ),
    )

    // ── German video sources ──────────────────────────────────────────────────
    // Verified: MagicShibby (UCwzLpT-Jk3yh19yzRG5OFPQ — confirmed valid Atom feed,
    //           15 entries with German titles, active April 2026)
    // Attempted but not verified: Trader Online (handle only), Sol4r1s (invalid ID),
    //           KüchenTisch Gaming, Der Spielraum Wien, BlackSet MTG
    //
    // Re-attempted 2026-07-14 (News feature improvements Phase 7) — no new source, no entity
    // added:
    //   @TraderOnline, @TraderOnlineMTG, @KuechenTischGaming — all HTTP 404, handles don't exist.
    //   @KuchenTischGaming (no umlaut, alt spelling) — resolves to UC5uRjXuo5KZAvRdDGb2SN9g,
    //                       channel title "Giuliano Rizzo (Welgar)", 0 entries — wrong/unrelated
    //                       channel.

    val videosDe = listOf(
        ContentSourceEntity(
            id = "default_video_magicshibby_de",
            name = "MagicShibby",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCwzLpT-Jk3yh19yzRG5OFPQ",
            type = "VIDEO",
            isDefault = true,
            language = "de",
        ),
    )

    val all = articles + articlesEs + articlesDe + videos + videosEs + videosDe
}
