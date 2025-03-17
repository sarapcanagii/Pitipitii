package com.sarapcanagii

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziPalV2 : MainAPI() {
    override var mainUrl = "https://dizipal909.com"
    override var name = "DiziPal V2"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override var sequentialMainPage = true

    // CloudFlare bypass
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    // Interceptor for Cloudflare
    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())

            // Check for Cloudflare protection
            if (doc.select("title").text() in listOf("Just a moment...", "Bir dakika lütfen...")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl" to "Son Bölümler",
        "$mainUrl/yabanci-dizi-izle" to "Yeni Diziler",
        "$mainUrl/hd-film-izle" to "Yeni Filmler",
        "$mainUrl/kanal/tabii" to "Tabii",
        "$mainUrl/kanal/netflix" to "Netflix",
        "$mainUrl/kanal/exxen" to "Exxen",
        "$mainUrl/kanal/disney" to "Disney+",
        "$mainUrl/kanal/amazon" to "Amazon Prime",
        "$mainUrl/kanal/tod" to "TOD (beIN)",
        "$mainUrl/kanal/hulu" to "Hulu",
        "$mainUrl/kanal/apple-tv" to "Apple TV+",
        "$mainUrl/anime" to "Anime"
    )

    // Main page data
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document

        val home = when {
            listOf(
                "$mainUrl/yabanci-dizi-izle",
                "$mainUrl/hd-film-izle",
                "$mainUrl/kanal/tabii",
                "$mainUrl/kanal/netflix",
                "$mainUrl/kanal/exxen",
                "$mainUrl/kanal/disney",
                "$mainUrl/kanal/amazon",
                "$mainUrl/kanal/tod",
                "$mainUrl/kanal/hulu",
                "$mainUrl/kanal/apple-tv",
                "$mainUrl/anime"
            ).any { request.data.contains(it) } -> {
                document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.toSeries() }
            }
            request.data.contains("$mainUrl") -> { // Son Bölümler
                document.select("a.relative.w-full.rounded-lg.flex.items-center.gap-4")
                    .mapNotNull { it.toLatestEpisodes() }
                    .take(18)
            }
            else -> emptyList()
        }

        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private suspend fun Element.toLatestEpisodes(): SearchResponse? {
        val name = selectFirst("div.text.block h2.text-white.text-sm.line-clamp-1.text-ellipsis.overflow-hidden.font-bold")?.text()
        val episode = selectFirst("div.text-white.text-sm.opacity-80.font-light")?.text()
        val title = "$name\n$episode"

        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href.substringBefore("/series"), TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun Element.toSeries(): SearchResponse? {
        val title = selectFirst("h2.text-white")?.text() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun SearchItem.toSearchResult(): SearchResponse {
        val title = this.title
        val href = "$mainUrl${this.url}"
        val posterUrl = this.poster

        return if (this.type == "series") {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val responseRaw = app.post(
            "$mainUrl/api/search-autocomplete",
            headers = mapOf("Accept" to "application/json, text/javascript, */*; q=0.01", "X-Requested-With" to "XMLHttpRequest"),
            referer = "$mainUrl/",
            data = mapOf("query" to query)
        )

        val searchItemsMap = jacksonObjectMapper().readValue<Map<String, SearchItem>>(responseRaw.text)
        return searchItemsMap.values.map { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val poster = fixUrlNull(document.selectFirst("div#mPlayerFds img")?.attr("src")
            ?: document.selectFirst("div.w-full.page-top.relative img")?.attr("src"))

        val year = document.selectXpath("//li[div[@class='key' and text()='Gösterim Yılı']]/div[@class='value']/text()")
            .text().trim().toIntOrNull()

        val description = document.selectFirst("div.summary p")?.text()?.trim()
        val tags = document.selectXpath("//li[div[@class='key' and normalize-space(text())='Kategoriler']]//div[@class='value']/a")
            .eachText().flatMap { it.trim().split(" ") }.filter { it.isNotEmpty() }

        val rating = document.selectXpath("//div[text()='IMDB Puanı']//following-sibling::div").text().trim().toRatingInt()
        val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Ortalama Süre']//following-sibling::div").text() ?: "")?.value?.toIntOrNull()

        return when {
            url.contains("/series/") -> {
                val title = document.selectFirst("h2.text-white.text-sm")?.text() ?: return null
                val episodes = document.select("div.relative.w-full.flex.items-start").mapNotNull {
                    val epName = it.selectFirst("div.text-white.text-sm.opacity-80.font-light")?.text()?.trim()
                    val epHref = fixUrlNull(it.selectFirst("a.text.block")?.attr("href"))
                    val epEpisode = it.selectFirst("div.text-white.text-sm.opacity-80.font-light")?.text()?.trim()?.split(" ")?.get(2)?.replace(".", "")?.toIntOrNull()
                    val epSeason = it.selectFirst("div.text-white.text-sm.opacity-80.font-light")?.text()?.trim()?.split(" ")?.get(0)?.replace(".", "")?.toIntOrNull()

                    if (epName != null && epHref != null) {
                        Episode(
                            data = epHref,
                            name = epName,
                            season = epSeason,
                            episode = epEpisode
                        )
                    } else null
                }

                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.rating = rating
                    this.duration = duration
                }
            }
            url.contains("/bolum/") -> {
                val title = document.selectFirst("h1.episode-title")?.text()?.trim() ?: return null
                val season = document.selectFirst("span.season-number")?.text()?.toIntOrNull()
                val episodeNumber = document.selectFirst("span.episode-number")?.text()?.toIntOrNull()

                newEpisodeLoadResponse(title, url, TvType.Episode) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.rating = rating
                    this.duration = duration
                    this.season = season
                    this.episode = episodeNumber
                }
            }
            else -> {
                val title = document.selectXpath("//div[@class='g-title'][2]/div").text().trim()

                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.rating = rating
                    this.duration = duration
                }
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZP", "Data: $data")
        val document = app.get(data).document
        val iframe = document.selectFirst(".series-player-container iframe")?.attr("src")
            ?: document.selectFirst("div#vast_new iframe")?.attr("src") ?: return false

        val iSource = app.get(iframe, referer = "$mainUrl/").text
        val m3uLink = Regex("""file:\"([^\"]+)""").find(iSource)?.groupValues?.get(1) ?: return false

        val subtitles = Regex("""\"subtitle\": \"([^\"]+)""").find(iSource)?.groupValues?.get(1)
        subtitles?.let {
            it.split(",").forEach { sub ->
                val subLang = sub.substringAfter("[").substringBefore("]")
                val subUrl = sub.replace("[$subLang]", "")
                subtitleCallback(SubtitleFile(lang = subLang, url = fixUrl(subUrl)))
            }
        }

        callback(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = m3uLink,
                referer = "$mainUrl/",
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}