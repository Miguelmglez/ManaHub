package com.mmg.magicfolder.feature.news.data.local

object DefaultSources {

    val articles = listOf(
        ContentSourceEntity(
            id = "default_article_wizards",
            name = "Wizards of the Coast",
            feedUrl = "https://magic.wizards.com/en/rss/rss.xml",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_mtggoldfish",
            name = "MTGGoldfish",
            feedUrl = "https://www.mtggoldfish.com/feed",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_edhrec",
            name = "EDHREC",
            feedUrl = "https://edhrec.com/articles/feed",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_scg",
            name = "Star City Games",
            feedUrl = "https://articles.starcitygames.com/feed",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_cardkingdom",
            name = "Card Kingdom Blog",
            feedUrl = "https://blog.cardkingdom.com/feed",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_draftsim",
            name = "Draftsim",
            feedUrl = "https://draftsim.com/feed",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_mtgazone",
            name = "MTG Arena Zone",
            feedUrl = "https://mtgazone.com/news/feed",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_mtgrocks",
            name = "MTG Rocks",
            feedUrl = "https://mtgrocks.com/feed",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_cranial",
            name = "Cranial Insertion",
            feedUrl = "https://cranial-insertion.com/feed",
            type = "ARTICLE",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_article_gatheringmagic",
            name = "GatheringMagic",
            feedUrl = "https://www.gatheringmagic.com/feed/",
            type = "ARTICLE",
            isDefault = true,
        ),
    )

    val videos = listOf(
        ContentSourceEntity(
            id = "default_video_mtg_official",
            name = "Magic: The Gathering",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCpwK0zGsMU0C9V_gPMNngSw",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_command_zone",
            name = "The Command Zone",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCLsiaNUb42gRAP7ewbJ0ecQ",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_tolarian",
            name = "Tolarian Community College",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC7-hR5EfgpM6oHfiGDkxfMA",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_mtggoldfish",
            name = "MTGGoldfish",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCVOzdxodlqNC2Y3KSvVQiVA",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_rhystic",
            name = "Rhystic Studies",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UC8e0Sg8TmRRFJytjEGhmVTg",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_nitpicking",
            name = "Nitpicking Nerds",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCiIYx9sFBPjq1P8VGkHDALQ",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_good_morning",
            name = "Good Morning Magic",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCvE8Mza7uRuIIqmMLGsz01g",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_pleasant_kenobi",
            name = "Pleasant Kenobi",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCkUELeIMduQbsv8MhmMEPpg",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_legenvd",
            name = "LegenVD",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCd0kth9C1hqSiaqoQ9TINaA",
            type = "VIDEO",
            isDefault = true,
        ),
        ContentSourceEntity(
            id = "default_video_loadingreadyrun",
            name = "LoadingReadyRun",
            feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCLBNH4hp-NaMcqc5M9MYqzA",
            type = "VIDEO",
            isDefault = true,
        ),
    )

    val all = articles + videos
}
