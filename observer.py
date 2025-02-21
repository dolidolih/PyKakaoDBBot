import os
import sys
import time
import json
import base64
import os
from helper.ObserverHelper import ObserverHelper, get_config
from helper.KakaoDB import KakaoDB
import subprocess

class Watcher(object):
    running = True
    refresh_delay_secs = 0.01

    def __init__(self, config,db):
        self._cached_stamp = 0
        self.db = db
        self.config = config
        self.watchfile = config["db_path"] + '/KakaoTalk.db-wal'
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
    config = get_config()
    watcher = Watcher(config,db)
    watcher.watch()

if __name__ == "__main__":
    main()
