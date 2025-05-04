version = 32

cloudstream {
    authors     = listOf("sarapcanagii")
    language    = "tr"
    description = "En yeni dizileri güvenli ve hızlı şekilde sunar."
    status  = 1
    tvTypes = listOf("TvSeries", "Movie")
    
    val mainFile = project.file("src/main/kotlin/com/Prueba/DiziPalV2.kt")
    val mainContent = mainFile.readText()
    val mainUrlRegex = """mainUrl\s*=\s*"https://([^"]+)"""".toRegex()
    
    val domain = mainUrlRegex.find(mainContent)?.groupValues?.get(1) 
        ?: throw GradleException("Domain DiziPalV2.kt dosyasından çekilemedi")
    
    iconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=32"
}