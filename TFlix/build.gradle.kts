version = 23

cloudstream {
    authors     = listOf("sarapcanagii")
    language    = "tr"
    description = "(TEST AŞAMASINDA) Watch live sports on TFLIX, including English Premier League, LaLiga, Bundesliga, F1, and live cricket. Stream your favorite events MHDTV Sports &amp; DaddyLiveHD instantly!"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live")
    iconUrl = "https://www.google.com/s2/favicons?domain=https://tv.tflix.app&sz=%size%"
}