const fs = require('fs');
const puppeteer = require('puppeteer');

async function getFirstGoogleResult(query) {
    const browser = await puppeteer.launch();
    const page = await browser.newPage();
    await page.goto(`https://www.google.com/search?q=${encodeURIComponent(query)}`);

    // İlk sonucun linkini al
    const firstResult = await page.evaluate(() => {
        const linkElement = document.querySelector('a');
        return linkElement ? linkElement.href : null;
    });

    await browser.close();
    return firstResult;
}

async function updateKotlinFileLink() {
    const kotlinFilePath = 'DiziPal/src/main/kotlin/com/Pitipitii/DiziPal.kt';
    const currentContent = fs.readFileSync(kotlinFilePath, 'utf8');

    const newLink = await getFirstGoogleResult("Dizipal");
    if (!newLink) {
        console.error("Link bulunamadı.");
        return;
    }

    // Kotlin dosyasında mainUrl değerini güncelle
    const updatedContent = currentContent.replace(/(mainUrl\s*=\s*")[^"]+(")/, `$1${newLink}$2`);

    fs.writeFileSync(kotlinFilePath, updatedContent, 'utf8');
    console.log("Dosya başarıyla güncellendi.");
}

updateKotlinFileLink().catch(console.error);