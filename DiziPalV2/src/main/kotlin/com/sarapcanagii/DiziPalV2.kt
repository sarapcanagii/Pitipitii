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
import org.jsoup.nodes.Document

class DiziPalV2 : MainAPI() {
    override var mainUrl              = "https://dizipal908.com"
    override var name                 = "DiziPal V2"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    
        // CloudFlare bypass
    override var sequentialMainPage = true // Ana sayfa yükleme sıralaması

    // CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() } // CloudFlare bypass nesnesi
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) } // Interceptor nesnesi

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response { // HTTP isteklerini engelleme
            val request = chain.request() // İstek alınıyor
            val response = chain.proceed(request) // İstek işleniyor
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string()) // Yanıtın gövdesi işleniyor

            if (doc.select("title").text() == "Just a moment..." || doc.select("title").text() == "Bir dakika lütfen...") {
                return cloudflareKiller.intercept(chain) // CloudFlare engeli varsa bypass et
            }

            return response // Yanıtı döndür
        }
    }

    override val mainPage = mainPageOf( // Ana sayfa içerik tanımları
        "${mainUrl}"                   to "Son Bölümler",
        "${mainUrl}/yabanci-dizi-izle" to "Yeni Diziler",
        "${mainUrl}/hd-film-izle"      to "Yeni Filmler",
        "${mainUrl}/kanal/tabii"       to "Tabii",
        "${mainUrl}/kanal/netflix"     to "Netflix",
        "${mainUrl}/kanal/exxen"       to "Exxen",
        "${mainUrl}/kanal/disney"      to "Disney+",
        "${mainUrl}/kanal/amazon"      to "Amazon Prime",
        "${mainUrl}/kanal/tod"         to "TOD (beIN)",
        "${mainUrl}/kanal/hulu"        to "Hulu",
        "${mainUrl}/kanal/apple-tv"    to "Apple TV+",
        "${mainUrl}/anime"             to "Anime",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val document = app.get(request.data).document

    // URL'ye göre hangi veri tipini çekeceğimizi belirleyelim
    val home = when {
        request.data.contains("${mainUrl}/yabanci-dizi-izle") -> { // Yeni Diziler
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/hd-film-izle") -> { // Yeni Filmler
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/kanal/tabii") -> { // Tabii
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/kanal/netflix") -> { // Netflix
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/kanal/exxen") -> { // Exxen
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/kanal/disney") -> { // Disney+
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/kanal/amazon") -> { // Amazon Prime
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/kanal/tod") -> { // TOD (beIN)
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/kanal/hulu") -> { // Hulu
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/kanal/apple-tv") -> { // Apple TV+
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        request.data.contains("${mainUrl}/anime") -> { // Anime
            document.select("div.p-1.rounded-md.prm-borderb").mapNotNull { it.diziler() }
        }
        // Diğer tüm kategoriler için aynı şekilde diziler() fonksiyonunu kullanabilirsin.
        request.data.contains("${mainUrl}") -> { // Son Bölümler
            document.select("a.relative.w-full.rounded-lg.flex.items-center.gap-4")
                .mapNotNull { it.sonBolumler() }
                .take(18)
        }
        else -> { // Varsayılan olarak boş döndürüyoruz
            emptyList()
        }
    }

    return newHomePageResponse(request.name, home, hasNext = false)
}

    private suspend fun Element.sonBolumler(): SearchResponse? { // Son bölümleri işleme
        val name = this.selectFirst("div.text.block h2.text-white.text-sm.line-clamp-1.text-ellipsis.overflow-hidden.font-bold")?.text() ?: return null // Dizi adı
        val episode = this.selectFirst("div.text-white.text-sm.opacity-80.font-light")?.text() ?: return null // Bölüm bilgisi
        val title = "$name\n$episode" // Başlık oluşturma

        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null // URL düzenleme
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src")) // Poster URL

        return newTvSeriesSearchResponse(title, href.substringBefore("/series"), TvType.TvSeries) { // Arama yanıtı oluşturma
            this.posterUrl = posterUrl
        }
    }

    private fun Element.diziler(): SearchResponse? { // Dizileri işleme
        val title = this.selectFirst("h2.text-white")?.text() ?: return null // Dizi başlığı
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null // URL düzenleme
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src")) // Poster URL

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl } // Arama yanıtı oluşturma
    }

  private fun SearchItem.toPostSearchResult(): SearchResponse { // Arama sonuçlarını işleme
        val title = this.title // Başlık al
        val href = "${mainUrl}${this.url}" // URL oluştur
        val posterUrl = this.poster // Poster URL

        return if (this.type == "series") { // Tür kontrolü
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> { // Arama işlemi
        val responseRaw = app.post( // Arama isteği gönder
            "${mainUrl}/api/search-autocomplete",
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            referer = "${mainUrl}/",
            data = mapOf("query" to query)
        )

        val searchItemsMap = jacksonObjectMapper().readValue<Map<String, SearchItem>>(responseRaw.text) // Yanıtı işle
        return searchItemsMap.values.map { it.toPostSearchResult() } // Arama sonuçlarını döndür
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query) // Hızlı arama

    override suspend fun load(url: String): LoadResponse? { // İçerik yükleme
        val document = app.get(url).document // Döküman al

        val poster = fixUrlNull(
            document.selectFirst("div#mPlayerFds img")?.attr("src")
            ?: document.selectFirst("div.w-full.page-top.relative img")?.attr("src")
        )// Poster URL
        // val year = document.selectXpath("//div[text()='Yapım Yılı']//following-sibling::div").text().trim().toIntOrNull()
        
        val year = document.selectXpath("//li[div[@class='key' and text()='Gösterim Yılı']]/div[@class='value']/text()")
    .text()
    .trim()
    .toIntOrNull()// Yapım yılı
        val description = document.selectFirst("div.summary p")?.text()?.trim() // Açıklama
        val tags = document.selectXpath("//li[div[@class='key' and normalize-space(text())='Kategoriler']]//div[@class='value']/a")
            .eachText() // Tüm <a> etiketlerinin metinlerini alır
            .flatMap { it.trim().split(" ") } // Boşluklara göre böler
            .filter { it.isNotEmpty() } // Boş olanları temizler // Etiketler
        val rating = document.selectXpath("//div[text()='IMDB Puanı']//following-sibling::div").text().trim().toRatingInt() // IMDB puanı
        val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Ortalama Süre']//following-sibling::div").text() ?: "")?.value?.toIntOrNull() // Süre

        return if (url.contains("/series/")) { // Dizi kontrolü
            val title = document.selectFirst("h2.text-white.text-sm")?.text() ?: return null // Başlık al

            val episodes = document.select("div.relative.w-full.flex.items-start").mapNotNull { // Bölümleri al
                val epName = it.selectFirst("div.text-white.text-sm.opacity-80.font-light")?.text()?.trim() ?: return@mapNotNull null // Bölüm adı
                val epHref = fixUrlNull(it.selectFirst("a.text.block")?.attr("href")) ?: return@mapNotNull null // Bölüm URL
                val epEpisode = it.selectFirst("div.text-white.text-sm.opacity-80.font-light")?.text()?.trim()?.split(" ")?.get(2)?.replace(".", "")?.toIntOrNull() // Bölüm numarası
                val epSeason = it.selectFirst("div.text-white.text-sm.opacity-80.font-light")?.text()?.trim()?.split(" ")?.get(0)?.replace(".", "")?.toIntOrNull() // Sezon numarası

                Episode(
                    data = epHref,
                    name = epName,
                    season = epSeason,
                    episode = epEpisode
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) { // Dizi yükleme yanıtı
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
            }
        } else {
            val title = document.selectXpath("//div[@class='g-title'][2]/div").text().trim() // Başlık al

            newMovieLoadResponse(title, url, TvType.Movie, url) { // Film yükleme yanıtı
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean { // Linkleri yükleme
        Log.d("DZP", "data » $data") // Debug log
        val document = app.get(data).document // Döküman al
        val iframe = document.selectFirst(".series-player-container iframe")?.attr("src")
            ?: document.selectFirst("div#vast_new iframe")?.attr("src") ?: return false // iFrame kontrolü
        Log.d("DZP", "iframe » $iframe") // Debug log

        val iSource = app.get(iframe, referer = "$mainUrl/").text // iFrame kaynağını al
        val m3uLink = Regex("""file:\"([^\"]+)""").find(iSource)?.groupValues?.get(1) // m3u8 linki ayıkla
        if (m3uLink == null) {
            Log.d("DZP", "iSource » $iSource") // Debug log
            return loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback) // Extractor yükle
        }

        val subtitles = Regex("""\"subtitle":\"([^\"]+)""").find(iSource)?.groupValues?.get(1) // Altyazıları ayıkla
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

        return true // Başarıyla link yüklendi
    }
}