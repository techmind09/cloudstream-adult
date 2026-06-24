package com.cloudstream-adult

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

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
            .mapNotNull { it.toMainPageResult() }.distinctBy { it.url }
        return newHomePageResponse(listOf(HomePageList(name = request.name, list = home, isHorizontalImages = true)), hasNext = home.isNotEmpty())
    }

    private fun org.jsoup.nodes.Element.toMainPageResult(): SearchResponse? {
        val link = selectFirst("a[href*='/en/'], a[href*='/dm']") ?: return null
        val url = fixUrlNull(link.attr("abs:href")) ?: return null
        val baseTitle = selectFirst("div.my-2 a, div.title a, a.text-secondary")?.text()?.trim() ?: link.text().trim()
        if (baseTitle.isBlank()) return null
        val posterUrl = fixUrlNull(selectFirst("img")?.let { it.attr("abs:data-src").ifEmpty { it.attr("abs:src") } }) ?: return null
        return newMovieSearchResponse(baseTitle, url, TvType.NSFW) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = "${mainUrl}/en/search/${query}${if (page > 1) "?page=$page" else ""}"
        val aramaCevap = app.get(url).document.select("div.grid.grid-cols-2 > div").mapNotNull { it.toMainPageResult() }
        return newSearchResponseList(aramaCevap, hasNext = true)
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.text-base")?.text()?.trim() ?: return null
        val poster = fixUrlNull(doc.selectFirst("meta[property='og:image']")?.attr("content"))
        return newMovieLoadResponse(title, url, TvType.NSFW, url) { this.posterUrl = poster }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val response = app.get(data)
        getAndUnpack(response.text).let { unpacked ->
            val playlistId = """/([a-f0-9\-]{36})/""".toRegex().find(unpacked)?.groupValues?.get(1)
            if (playlistId != null) {
                callback.invoke(newExtractorLink("MissAV", "MissAV", "https://surrit.com/$playlistId/playlist.m3u8", ExtractorLinkType.M3U8) {
                    this.referer = "$mainUrl/"
                })
            }
        }

        try {
            val title = response.document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
            val javCode = "([a-zA-Z]+-\\d+)".toRegex().find(title)?.groups?.get(1)?.value
            if (!javCode.isNullOrEmpty()) {
                val subDoc = app.get("$subtitleCatUrl/index.php?search=$javCode", timeout = 15).document
                val subLink = subDoc.select("td a").find { it.text().contains(javCode, ignoreCase = true) }?.attr("href")
                
                if (subLink != null) {
                    val pDoc = app.get("$subtitleCatUrl/$subLink", timeout = 10).document
                    pDoc.select(".col-md-6.col-lg-4").forEach { item ->
                        val langName = item.select(".sub-single span:nth-child(2)").text().lowercase()
                        val downloadBtn = item.select(".sub-single span:nth-child(3) a")
                        
                        // Yahan 'en' aur 'hi' map kar rahe hain
                        val langCode = when {
                            langName.contains("english") -> "en"
                            langName.contains("hindi") -> "hi"
                            else -> null
                        }

                        if (langCode != null && downloadBtn.isNotEmpty() && downloadBtn[0].text() == "Download") {
                            val subUrl = "$subtitleCatUrl${downloadBtn[0].attr("href")}"
                            subtitleCallback.invoke(SubtitleFile(langCode, subUrl))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.error(e) }
        return true
    }
}
