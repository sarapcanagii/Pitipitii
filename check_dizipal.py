# Bu kod sarapcanagii ve primatzeka' ya aittir. İstediginiz gibi kullanabilirsiniz.

import requests
import re
import os
from urllib.parse import urlparse
import time
import random
import sys

def get_cloudflare_session():
    return requests.Session()

def check_url(url, timeout=10):
    session = get_cloudflare_session()
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.5',
    }
    
    try:
        print(f"Checking URL: {url}")
        response = session.get(url, headers=headers, timeout=timeout, allow_redirects=True)
        
        final_url = response.url
        if final_url != url:
            print(f"Redirected to: {final_url}")
            final_number = int(re.search(r'dizipal(\d+)', final_url).group(1))
            original_number = int(re.search(r'dizipal(\d+)', url).group(1))
            
            if final_number > original_number:
                print(f"Found newer URL: {final_url}")
                return False
        
        return response.status_code in [200, 403]
    except requests.RequestException as e:
        print(f"Error checking {url}: {str(e)}")
        return False

def update_files(kt_file_path, gradle_file_path):
    try:
        if not os.path.exists(kt_file_path):
            print(f"Error: {kt_file_path} not found")
            return False
        
        if not os.path.exists(gradle_file_path):
            print(f"Error: {gradle_file_path} not found")
            return False

        print(f"Kotlin file path: {kt_file_path}")
        print(f"Gradle file path: {gradle_file_path}")

        with open(kt_file_path, 'r', encoding='utf-8') as f:
            kt_content = f.read()

        url_match = re.search(r'override var mainUrl = "(https://dizipal\d+\.com)"', kt_content)
        if not url_match:
            print("Error: URL pattern not found in DizipalV2.kt")
            return False

        current_url = url_match.group(1)
        print(f"Current URL: {current_url}")

        if check_url(current_url):
            print("Current URL is working fine and is the latest version")
            return False

        base_number = int(re.search(r'dizipal(\d+)', current_url).group(1))
        max_attempts = 5
        working_url = None

        for i in range(base_number + 1, base_number + max_attempts + 1):
            new_url = f"https://dizipal{i}.com"
            print(f"Trying {new_url}")
            
            if check_url(new_url):
                working_url = new_url
                print(f"Found working URL: {working_url}")
                break

        if working_url:
            # Update DizipalV2.kt
            new_kt_content = kt_content.replace(current_url, working_url)
            with open(kt_file_path, 'w', encoding='utf-8') as f:
                f.write(new_kt_content)
            print(f"Updated {kt_file_path}")

            # Update build.gradle.kts
            with open(gradle_file_path, 'r', encoding='utf-8') as f:
                gradle_content = f.read()
                print("Current gradle content:")
                print(gradle_content)

            version_match = re.search(r'version\s*=\s*(\d+)', gradle_content)
            if version_match:
                current_version = int(version_match.group(1))
                new_version = current_version + 1
                print(f"Updating version from {current_version} to {new_version}")
                
                new_gradle_content = gradle_content.replace(
                    f'version = {current_version}',
                    f'version = {new_version}'
                )
                
                with open(gradle_file_path, 'w', encoding='utf-8') as f:
                    f.write(new_gradle_content)
                print(f"Updated {gradle_file_path}")
            else:
                print("Warning: Version pattern not found in build.gradle.kts")
            
            return True
        
        print("No working URL found after maximum attempts")
        return False

    except Exception as e:
        print(f"Error: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    kt_path = "DizipalV2/src/main/kotlin/com/Prueba/DizipalV2.kt"
    gradle_path = "DizipalV2/build.gradle.kts"  # Düzeltilmiş gradle dosya yolu
    
    print("Starting URL check process...")
    if update_files(kt_path, gradle_path):
        print("Files updated successfully")
        sys.exit(0)
    else:
        print("No updates needed or process failed")
        sys.exit(1)