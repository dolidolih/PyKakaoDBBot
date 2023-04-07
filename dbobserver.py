import os
import sys
import time
from dbcon import KakaoDB
from kakaodecrypt import KakaoDecrypt
import json
import base64
import requests

class Watcher(object):
    running = True
    refresh_delay_secs = 0.01

    def __init__(self, watch_file,db):
        self._cached_stamp = 0
        self.filename = watch_file
        self.db = db

    def look(self):
        stamp = os.stat(self.filename).st_mtime
        if stamp != self._cached_stamp:
            self._cached_stamp = stamp
            custom_action(self.db)

    def watch(self):
        while self.running:
            try:
                time.sleep(self.refresh_delay_secs)
                self.look()
            except KeyboardInterrupt:
                print('\nDone')
                break
            except:
                print('Unhandled error: %s' % sys.exc_info()[0])

def custom_action(db):
    global last_log_id
    if last_log_id == 0:
        limit = 1
    else:
        limit = 5
    db.cur.execute(f'select * from chat_logs order by id desc limit ?',[limit])
    description = [desc[0] for desc in db.cur.description]
    res = db.cur.fetchall()
    res.reverse()
    for row in res:
        if row[0] > last_log_id:
            last_log_id = row[0]
            v = json.loads(row[13])
            enc = v["enc"]
            origin = v["origin"]
            enc_msg = row[5]
            user_id = row[4]
            dec_msg = KakaoDecrypt.decrypt(enc,enc_msg,user_id)
            if is_command(dec_msg):
                print("is_command")
                chat_id = row[3]
                user_info = get_user_info(db,chat_id,user_id)
                room = user_info[0]
                sender = user_info[1]
                if room == "my_name":
                    room = sender
                print("posting")
                post_data = make_post_data(row, dec_msg, room, sender, {description[i]:row[i] for i in range(len(row))})
                requests.post("http://127.0.0.1:5000/db",data={"data":post_data})
                print("posted")

def is_command(msg):
    with open('/home/dolidoli/www/commands_db','r') as fo:
        commands = json.loads(fo.read())
    if msg.split(' ')[0] in commands:
        return True
    else:
        return False

def get_user_info(db,chat_id,user_id):
    db.cur.execute(f'SELECT name FROM db2.open_link WHERE id = (SELECT link_id FROM chat_rooms WHERE id = ?);',[chat_id])
    room = db.cur.fetchall()[0][0]
    if user_id == 326419021:
        sender = "not angry ryan"
    else:
        sender = db.get_name_of_user_id(user_id)
    return (room, sender)

def make_post_data(row, dec_msg, room, sender, js):
    data = {"msg" : dec_msg,
            "room" : room,
            "sender" : sender,
            "log_id" : row[1],
            "chat_id" : row[3],
            "hash" : row[4],
            "user_id" : row[4],
            "is_DB" : True,
            "json" : js
            }
    return json.dumps(data)

def main(db):
    watch_file = '/home/dolidoli/.local/share/waydroid/data/data/com.kakao.talk/databases/KakaoTalk.db-wal'
    global last_log_id
    last_log_id = 0
    watcher = Watcher(watch_file,db)
    watcher.watch()

db = KakaoDB()
main(db)
