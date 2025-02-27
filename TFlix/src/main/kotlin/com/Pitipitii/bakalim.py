# Gerekli kütüphanelerin içe aktarılması
from Kekik.cli import konsol
from cloudscraper import CloudScraper
from parsel import Selector
from re import findall

# CloudScraper oturumu oluşturuluyor
oturum = CloudScraper()

# Ana URL ve sayfa URL'si tanımlanıyor
mainUrl = "https://dizipal903.com"
pageUrl = f"{mainUrl}/yabanci-dizi-izle"

# Sayfanın içeriği istek yapılarak alınıyor
istek = oturum.get(pageUrl)

# Gelen sayfa içeriği bir Selector nesnesine dönüştürülüyor
secici = Selector(istek.text)

# İçerik bilgilerini alıp konsola yazdıran fonksiyon
def icerik_ver(secici: Selector):
    son_date = ""

    # Her içerik için gerekli bilgilerin konsola yazdırılması
    for icerik in secici.css("div.p-1.rounded-md.prm-borderb"):
        konsol.print(icerik.css("h2.text-white::text").get())  # Başlık
        konsol.print(icerik.css("a::attr(href)").get())     # Link
        konsol.print(icerik.css("img::attr(data-src)").get())    # Görsel kaynağı
        konsol.print(icerik.css("span.text-white.text-sm::text").get())  # Tarih
        konsol.print("\n")
        son_date = icerik.css("span.text-white.text-sm::text").get()  # Son tarihin güncellenmesi

    return son_date

# İlk içerik verileri alınarak son tarih güncelleniyor
son_date = icerik_ver(secici)

# Daha fazla içerik verisi almak için API çağrısı yapan fonksiyon
def devam_ver(son_date) -> str:
    # POST isteği ile yeni içerik verisi alınıyor
    istek = oturum.post(
        url = f"{mainUrl}/api/load-series",
        data = {
            "date": son_date,
            "tur": findall(r"tur=([\d]+)&", pageUrl)[0],  # Tür bilgisi sayfa URL'sinden alınıyor
            "durum": "",
            "kelime": "",
            "type": "",
            "siralama": ""
        }
    )
    veri = istek.json()
    if not veri.get("html"):  # Eğer yeni içerik yoksa boş string döndürülüyor
        return ""

    # Yeni içerik HTML yapısına dönüştürülüyor
    devam_html = "<article class='type2'><ul>" + veri["html"] + "</ul></article>"

    # Yeni içerikler işleniyor
    return icerik_ver(Selector(devam_html))

# İçerikler tükenene kadar döngü devam ediyor
while son_date:
    son_date = devam_ver(son_date)