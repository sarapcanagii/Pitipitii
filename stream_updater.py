# Bu kod sarapcanagii ve primatzeka' ya aittir. İstediginiz gibi kullanabilirsiniz.

import requests
import json
import logging
import os
import re
from datetime import datetime
from git import Repo

class StreamUpdater:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        })
        
        self.repo_path = os.getenv('GITHUB_WORKSPACE')
        self.m3u8_path = os.path.join(self.repo_path, 'NeonSpor/NeonSpor.m3u8')
        
    def get_new_domain(self):
        try:
            response = self.session.get('https://casintrotv12.com/domain.php')
            if response.status_code == 200:
                data = response.json()
                # baseurl'den domain kısmını çıkar
                new_domain = data.get('baseurl', '').rstrip('/')
                return new_domain
            else:
                logging.error(f"API yanıt vermedi. Status code: {response.status_code}")
        except Exception as e:
            logging.error(f"Domain alınamadı: {str(e)}")
        return None

    def read_m3u8_file(self):
        try:
            with open(self.m3u8_path, 'r', encoding='utf-8') as file:
                return file.read()
        except Exception as e:
            logging.error(f"M3U8 dosyası okunamadı: {str(e)}")
            return None

    def update_m3u8_content(self, content, new_domain):
        try:
            # Mevcut domain'i bul
            current_domain_pattern = r'https://[^/]+/yayin'
            matches = re.findall(current_domain_pattern, content)
            if not matches:
                logging.error("Mevcut domain bulunamadı")
                return content

            current_domain = matches[0].rsplit('/yayin', 1)[0]
            
            if current_domain == new_domain:
                logging.info("Domain zaten güncel")
                return content

            # Tüm domain referanslarını değiştir
            updated_content = content.replace(current_domain, new_domain)
            logging.info(f"Domain güncellendi: {current_domain} -> {new_domain}")
            return updated_content

        except Exception as e:
            logging.error(f"İçerik güncelleme hatası: {str(e)}")
            return content

    def commit_and_push_changes(self):
        try:
            repo = Repo(self.repo_path)
            repo.index.add([self.m3u8_path])
            commit_message = "🛠️ Linkler bot tarafından güncellendi"
            repo.index.commit(commit_message)
            origin = repo.remote('origin')
            origin.push()
            logging.info("Değişiklikler başarıyla commit ve push edildi")
        except Exception as e:
            logging.error(f"Git işlemleri sırasında hata: {str(e)}")

    def update_streams(self):
        new_domain = self.get_new_domain()
        if not new_domain:
            logging.error("Yeni domain alınamadı")
            return

        current_content = self.read_m3u8_file()
        if not current_content:
            return

        updated_content = self.update_m3u8_content(current_content, new_domain)
        
        if updated_content != current_content:
            try:
                with open(self.m3u8_path, 'w', encoding='utf-8') as file:
                    file.write(updated_content)
                self.commit_and_push_changes()
                logging.info("Stream domainleri başarıyla güncellendi")
            except Exception as e:
                logging.error(f"Dosya yazma hatası: {str(e)}")
        else:
            logging.info("Güncelleme gerekmiyor")

def main():
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(levelname)s - %(message)s'
    )
    updater = StreamUpdater()
    updater.update_streams()

if __name__ == "__main__":
    main()