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
    override var mainUrl = "https://dizipal904.com"
    override var name = "DiziPal V2"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    // CloudFlare bypass
    override var sequentialMainPage = true

    // CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.select("title").text() == "Just a moment..." || doc.select("title").text() == "Bir dakika lütfen...") {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/yabanci-dizi-izle" to "Son Bölümler",
        "${mainUrl}/yabanci-dizi-izle" to "Yeni Diziler",
        "${mainUrl}/hd-film-izle" to "Yeni Filmler",
        "${mainUrl}/kanal/tabii" to "Tabii",
        "${mainUrl}/kanal/netflix" to "Netflix",
        "${mainUrl}/kanal/exxen" to "Exxen",
        "${mainUrl}/kanal/disney" to "Disney+",
        "${mainUrl}/kanal/amazon" to "Amazon Prime",
        "${mainUrl}/kanal/tod" to "TOD (beIN)",
        "${mainUrl}/kanal/hulu" to "Hulu",
        "${mainUrl}/kanal/apple-tv" to "Apple TV+",
        "${mainUrl}/anime" to "Anime",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = if (request.data.contains("/yabanci-dizi-izle")) {
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.sonBolumler() }
        } else {
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }

        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private suspend fun Element.sonBolumler(): SearchResponse? {
        val name = this.selectFirst("h2.text-white")?.text() ?: return null
        val episode = this.selectFirst("span.text-white.text-sm")?.text() ?: return null
        val title = "$name\n$episode"

        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href.substringBefore("/sezon"), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.diziler(): SearchResponse? {
        val title = this.selectFirst("h2.text-white")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun SearchItem.toPostSearchResult(): SearchResponse {
        val title = this.title
        val href = "${mainUrl}${this.url}"
        val posterUrl = this.poster

        return if (this.type == "series") {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val responseRaw = app.post(
            "${mainUrl}/api/search-autocomplete",
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            referer = "${mainUrl}/",
            data = mapOf("query" to query)
        )

        val searchItemsMap = jacksonObjectMapper().readValue<Map<String, SearchItem>>(responseRaw.text)
        return searchItemsMap.values.map { it.toPostSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val poster = fixUrlNull(document.selectFirst("img")?.attr("src"))
        val year = document.selectXpath("//li[div[@class='key' and text()='Gösterim Yılı']]/div[@class='value']/text()")
    .firstOrNull()  // Liste boşsa null döndürür
    ?.text()        // null değilse metni alır
    ?.trim()        // null değilse boşlukları temizler
    ?.toIntOrNull() // null değilse integer'a çevirir

if (year == null) {
    println("Gösterim yılı bulunamadı veya geçersiz.")
} else {
    println("Gösterim yılı: $year")
}
        val description = document.selectFirst("div.summary p")?.text()?.trim()
        val tags = document.selectXpath("//li[div[@class='key' and normalize-space(text())='Kategoriler']]//div[@class='value']/a")
    .eachText() // Tüm <a> etiketlerinin metinlerini alır
    .flatMap { it.trim().split(" ") } // Boşluklara göre böler
    .filter { it.isNotEmpty() } // Boş olanları temizler
        val rating = document.selectXpath("//div[text()='IMDB Puanı']//following-sibling::div").text().trim().toRatingInt()
        val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Ortalama Süre']//following-sibling::div").text() ?: "")?.value?.toIntOrNull()

        return if (url.contains("/dizi/")) {
            val title = document.selectFirst("div.cover h5")?.text() ?: return null

            val episodes = document.select("div.episode-item").mapNotNull {
                val epName = it.selectFirst("div.name")?.text()?.trim() ?: return@mapNotNull null
                val epHref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val epEpisode = it.selectFirst("div.episode")?.text()?.trim()?.split(" ")?.get(2)?.replace(".", "")?.toIntOrNull()
                val epSeason = it.selectFirst("div.episode")?.text()?.trim()?.split(" ")?.get(0)?.replace(".", "")?.toIntOrNull()

                Episode(
                    data = epHref,
                    name = epName,
                    season = epSeason,
                    episode = epEpisode
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
            }
        } else {
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

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZP", "data » $data")
        val document = app.get(data).document
        val iframe = document.selectFirst(".series-player-container iframe")?.attr("src")
            ?: document.selectFirst("div#vast_new iframe")?.attr("src") ?: return false
        Log.d("DZP", "iframe » $iframe")

        val iSource = app.get(iframe, referer = "$mainUrl/").text
        val m3uLink = Regex("""file:\"([^\"]+)""").find(iSource)?.groupValues?.get(1)
        if (m3uLink == null) {
            Log.d("DZP", "iSource » $iSource")
            return loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
        }

        val subtitles = Regex("""\"subtitle":\"([^\"]+)""").find(iSource)?.groupValues?.get(1)
        subtitles?.let {
            if (it.contains(",")) {
                it.split(",").forEach { sub ->
                    val subLang = sub.substringAfter("[").substringBefore("]")
                    val subUrl = sub.replace("[$subLang]", "")

                    subtitleCallback(
                        SubtitleFile(
                            lang = subLang,
                            url = fixUrl(subUrl)
                        )
                    )
                }
            } else {
                val subLang = it.substringAfter("[").substringBefore("]")
                val subUrl = it.replace("[$subLang]", "")

                subtitleCallback(
                    SubtitleFile(
                        lang = subLang,
                        url = fixUrl(subUrl)
                    )
                )
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