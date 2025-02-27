version = 22

cloudstream {
    authors     = listOf("sarapcanagii")
    language    = "tr"
    description = "HDFilmCehennemi, en yeni filmleri ve yabancı dizileri HD kalitesinde izleyebileceğiniz ücretsiz bir platformdur."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries", "Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=https://hdfilmcehennemi10.org&sz=%size%"
}
