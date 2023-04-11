import os
import sys
import time
from dbcon import KakaoDB
import json
import base64
import os
from observerhelper import ObserverHelper, get_config

CONFIG_FILE = 'config.json'
HOME_PATH = os.getenv('HOME')
DB_PATH = f'{HOME_PATH}/.local/share/waydroid/data/data/com.kakao.talk/databases'

class Watcher(object):
    running = True
    refresh_delay_secs = 0.01

    def __init__(self, config,db):
        self._cached_stamp = 0
        self.db = db
        self.config = config
        self.watchfile = DB_PATH + '/KakaoTalk.db-wal'
        self.helper = ObserverHelper(config)

    def look(self):
        stamp = os.stat(self.watchfile).st_mtime
        if stamp != self._cached_stamp:
            self._cached_stamp = stamp
            self.helper.check_change(self.db)

    def watch(self):
        while self.running:
            time.sleep(self.refresh_delay_secs)
            self.look()

def main():
    db = KakaoDB()
    config = get_config(CONFIG_FILE)
    watcher = Watcher(config,db)
    watcher.watch()

if __name__ == "__main__":
    main()