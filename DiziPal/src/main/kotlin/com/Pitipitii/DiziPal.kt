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

class DiziPal : MainAPI() {
    override var mainUrl = "https://dizipal1225.com"
    override var name = "DiziPal"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override var sequentialMainPage = true

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
        "${mainUrl}/diziler/son-bolumler" to "Son Bölümler",
        "${mainUrl}/diziler" to "Yeni Diziler",
        "${mainUrl}/filmler" to "Yeni Filmler",
        "${mainUrl}/koleksiyon/netflix" to "Netflix",
        "${mainUrl}/koleksiyon/exxen" to "Exxen",
        "${mainUrl}/koleksiyon/blutv" to "BluTV",
        "${mainUrl}/koleksiyon/disney" to "Disney+",
        "${mainUrl}/koleksiyon/amazon-prime" to "Amazon Prime",
        "${mainUrl}/koleksiyon/tod-bein" to "TOD (beIN)",
        "${mainUrl}/koleksiyon/gain" to "Gain",
        "${mainUrl}/tur/mubi" to "Mubi",
        "${mainUrl}/diziler?kelime=&durum=&tur=26&type=&siralama=" to "Anime",
        "${mainUrl}/diziler?kelime=&durum=&tur=5&type=&siralama=" to "Bilimkurgu Dizileri",
        "${mainUrl}/tur/bilimkurgu" to "Bilimkurgu Filmleri",
        "${mainUrl}/diziler?kelime=&durum=&tur=11&type=&siralama=" to "Komedi Dizileri",
        "${mainUrl}/tur/komedi" to "Komedi Filmleri",
        "${mainUrl}/diziler?kelime=&durum=&tur=4&type=&siralama=" to "Belgesel Dizileri",
        "${mainUrl}/tur/belgesel" to "Belgesel Filmleri",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = if (request.data.contains("/diziler/son-bolumler")) {
            document.select("div.episode-item").mapNotNull { it.sonBolumler() }
        } else {
            document.select("article.type2 ul li").mapNotNull { it.diziler() }
        }

        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private suspend fun Element.sonBolumler(): SearchResponse? {
        val name = this.selectFirst("div.name")?.text() ?: return null
        val episode = this.selectFirst("div.episode")?.text()?.trim()?.replace(". Sezon ", "x")?.replace(". Bölüm", "") ?: return null
        val title = "$name $episode"

        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href.substringBefore("/sezon"), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.diziler(): SearchResponse? {
        val title = this.selectFirst("span.title")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

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

        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val year = document.selectXpath("//div[text()='Yapım Yılı']//following-sibling::div").text().trim().toIntOrNull()
        val description = document.selectFirst("div.summary p")?.text()?.trim()
        val tags = document.selectXpath("//div[text()='Türler']//following-sibling::div").text().trim().split(" ").mapNotNull { it.trim() }
        val rating = document.selectXpath("//div[text()='IMDB Puanı']//following-sibling::div").text().trim()
        val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Ortalama Süre']//following-sibling::div").text() ?: "")?.value?.toIntOrNull()

        return if (url.contains("/dizi/")) {
            val title = document.selectFirst("div.cover h5")?.text() ?: return null

            val episodes = document.select("div.episode-item").mapNotNull {
                val epName = it.selectFirst("div.name")?.text()?.trim() ?: return@mapNotNull null
                val epHref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val epEpisode = it.selectFirst("div.episode")?.text()?.trim()?.split(" ")?.get(2)?.replace(".", "")?.toIntOrNull()
                val epSeason = it.selectFirst("div.episode")?.text()?.trim()?.split(" ")?.get(0)?.replace(".", "")?.toIntOrNull()

                newEpisode(epHref) {
                    this.name = epName
                    this.season = epSeason
                    this.episode = epEpisode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.duration = duration
            }
        } else {
            val title = document.selectXpath("//div[@class='g-title'][2]/div").text().trim()

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.duration = duration
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DZP", "data » $data")
        
        try {
            val document = app.get(data).document
            val iframe = document.selectFirst(".series-player-container iframe")?.attr("src")
                ?: document.selectFirst("div#vast_new iframe")?.attr("src") ?: return false
            Log.d("DZP", "iframe » $iframe")
    
            val iSource = app.get(
                iframe,
                referer = "$mainUrl/",
                headers = mapOf(
                    "Accept" to "*/*",
                    "Sec-Fetch-Site" to "cross-site",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Dest" to "empty"
                )
            ).text
    
            val m3uLink = Regex("""(?:file|source):[\s"]*([^"'\s]+)""", RegexOption.IGNORE_CASE)
                .find(iSource)?.groupValues?.get(1)
    
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
    
            if (!m3uLink.isNullOrBlank() && (m3uLink.contains(".m3u8") || m3uLink.contains("/hls/"))) {
                val finalUrl = if (!m3uLink.startsWith("http")) {
                    if (m3uLink.startsWith("//")) "https:$m3uLink"
                    else "${mainUrl}${if (!m3uLink.startsWith("/")) "/" else ""}$m3uLink"
                } else m3uLink
    
                // Sadece master playlist'i (tüm kaliteleri içeren) ekle
                val m3u8Links = M3u8Helper.generateM3u8(
                    source = this.name,
                    streamUrl = finalUrl,
                    referer = iframe,
                    headers = mapOf(
                        "Referer" to iframe,
                        "Origin" to mainUrl,
                        "User-Agent" to USER_AGENT
                    )
                )
                
                // Sadece quality == Qualities.Unknown olan linki ekle (bu master playlist'tir)
                m3u8Links.filter { it.quality == Qualities.Unknown.value }.forEach(callback)
                
                return true
            } else {
                return loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
            }
        } catch (e: Exception) {
            Log.e("DZP", "M3U8 extraction failed: ${e.message}")
            return loadExtractor(data, "$mainUrl/", subtitleCallback, callback)
        }
    }
}