version = 7

cloudstream {
    authors     = listOf("sarapcanagii")
    language    = "tr"
    description = "(BeIN Sport, Tivibu Spor ve Smart Spor için VPN Gerekebilir.) NeonSpor eklentisini de BeIN Sport, Tabii Spor, Tivibu Spor, Smart Spor, S Sport ve Diğer kanallar mevcuttur."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live")
    iconUrl = "https://www.google.com/s2/favicons?domain=colorhunt.co&sz=%size%"
}