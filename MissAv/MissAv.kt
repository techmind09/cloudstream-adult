// ! Bu araç @ByAyzen tarafından | @Cs-GizliKeyif için yazılmıştır.

package com.byayzen

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.nodes.Element

class MissAV : MainAPI() {
    override var mainUrl = "https://missav.live"
    override var name = "MissAV"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.NSFW)
    val subtitleCatUrl = "https://www.subtitlecat.com"

    override val mainPage = mainPageOf(
        "$mainUrl/dm169/en/weekly-hot?sort=weekly_views" to "Weekly Hot",
        "$mainUrl/dm263/en/monthly-hot?sort=views" to "Monthly Hot",
        "$mainUrl/en/new?sort=published_at" to "Newly Added",
        "$mainUrl/en/english-subtitle" to "English Subtitles",
        "$mainUrl/dm628/en/uncensored-leak" to "Uncensored Leak",
        "$mainUrl/dm514/en/new" to "Recent Update",
        "$mainUrl/dm588/en/release" to "New Release",
        "$mainUrl/dm291/en/today-hot" to "Most Viewed Today"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}${if (request.data.contains("?")) "&" else "?"}page=$page"

        val document = app.get(url).document

        val home = document.select("div.grid.grid-cols-2 > div, div.thumbnail.group")
            .mapNotNull { it.toMainPageResult() }
            .distinctBy { it.url }

        return newHomePageResponse(
            list = listOf(
                HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = true
                )
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val link = selectFirst("a[href*='/en/'], a[href*='/dm']") ?: return null
        val url = fixUrlNull(link.attr("abs:href")) ?: return null

        val baseTitle = selectFirst("div.my-2 a, div.title a, a.text-secondary")?.text()?.trim()
            ?: link.text().trim()

        if (baseTitle.isBlank()) return null

        val blacklist = listOf("Recent update", "Contact", "Support", "DMCA", "Home")
        if (blacklist.any { baseTitle.equals(it, ignoreCase = true) }) return null

        val isUncensored = (link.attr("alt") + link.attr("href") + this.outerHtml())
            .contains(Regex("uncensored[-_ ]?leak", RegexOption.IGNORE_CASE))

        val title = if (isUncensored && !baseTitle.startsWith("Uncensored - ", ignoreCase = true))
            "Uncensored - $baseTitle" else baseTitle

        val posterUrl = fixUrlNull(
            selectFirst("img")?.let {
                it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
            }
        )

        if (posterUrl == null) return null

        return newMovieSearchResponse(title, url, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page == 1) {
            "${mainUrl}/en/search/${query}"
        } else {
            "${mainUrl}/en/search/${query}?page=$page"
        }

        val document = app.get(url).document

        val aramaCevap =
            document.select("div.grid.grid-cols-2 > div").mapNotNull { it.toMainPageResult() }

        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.text-base")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
        val year = document.selectFirst("time")?.text()?.split("-")?.firstOrNull()?.toIntOrNull()

        val tags = document.select("div.text-secondary:contains(genre) a").map {
            it.text().trim() }
        val actresses = document.select("div.text-secondary:contains(actress) a").map {
            Actor(it.text().trim()) }
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.year = year
            this.tags = tags
            addActors(actresses)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data)
        getAndUnpack(response.text).let { unpacked ->
            val playlistId = """/([a-f0-9\-]{36})/""".toRegex().find(unpacked)?.groupValues?.get(1)

            if (playlistId != null) {
                callback.invoke(
                    newExtractorLink(
                        source = "MissAV",
                        name = "MissAV",
                        url = "https://surrit.com/$playlistId/playlist.m3u8",
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.headers = mapOf("Referer" to "$mainUrl/")
                    }
                )
            }
        }

        try {
            val doc = response.document
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
            val javCode = "([a-zA-Z]+-\\d+)".toRegex().find(title)?.groups?.get(1)?.value
            if(!javCode.isNullOrEmpty())
            {
                val query = "$subtitleCatUrl/index.php?search=$javCode"
                val subDoc = app.get(query, timeout = 15).document
                val subList = subDoc.select("td a")
                for(item in subList)
                {
                    if(item.text().contains(javCode,ignoreCase = true))
                    {
                        val fullUrl = "$subtitleCatUrl/${item.attr("href")}"
                        val pDoc = app.get(fullUrl, timeout = 10).document
                        val sList = pDoc.select(".col-md-6.col-lg-4")
                        for(item in sList)
                        {
                            try {
                                val language = item.select(".sub-single span:nth-child(2)").text()
                                val text = item.select(".sub-single span:nth-child(3) a")
                                if(text != null && text.size > 0 && text[0].text() == "Download")
                                {
                                    val url = "$subtitleCatUrl${text[0].attr("href")}"
                                    subtitleCallback.invoke(
                                        SubtitleFile(
                                            language.replace("\uD83D\uDC4D \uD83D\uDC4E",""),  // Use label for the name
                                            url     // Use extracted URL
                                        )
                                    )
                                }
                            } catch (e: Exception) { }
                        }

                    }
                }

            }
        } catch (e: Exception) { }
        return true
    }
}
