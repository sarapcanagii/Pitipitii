version = 4

cloudstream {
    authors     = listOf("sarapcanagii")
    language    = "tr"
    description = "NeonSpor eklentisini de BeIN Spor, Tabii Spor ve S Spor kanalları mevcuttur."

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