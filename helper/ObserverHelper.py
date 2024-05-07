import json
import requests
from helper.KakaoDB import KakaoDB

class ObserverHelper:
    def __init__(self):
        self.last_log_id = 0
        self.config = get_config()
        self.BOT_ID = self.config["bot_id"]
        self.BOT_NAME = self.config["bot_name"]

    def make_post_data(self, dec_msg: str, room: str, sender: str, js: dict) -> str:
        data = {"msg" : dec_msg,
                "room" : room,
                "sender" : sender,
                "json" : js
                }
        return json.dumps(data)
    
    def check_change(self, db: KakaoDB) -> None:
        if self.last_log_id == 0:
            db.cur.execute(f'select _id from chat_logs order by _id desc limit 1')
            self.last_log_id = db.cur.fetchall()[0][0]
            return
        db.cur.execute(f'select * from chat_logs where _id > ? order by _id asc',[self.last_log_id])
        description = [desc[0] for desc in db.cur.description]
        res = db.cur.fetchall()

        for row in res:
            if row[0] > self.last_log_id:
                self.last_log_id = row[0]
                v = json.loads(row[13])
                enc = v["enc"]
                origin = v["origin"]
                enc_msg = row[5]
                user_id = row[4]
                dec_msg = db.decrypt(enc,enc_msg,user_id)
                chat_id = row[3]
                user_info = db.get_user_info(chat_id,user_id)
                room = user_info[0]
                sender = user_info[1]
                if room == self.BOT_NAME:
                    room = sender
                post_data = self.make_post_data(dec_msg, room, sender, {description[i]:row[i] for i in range(len(row))})
                try:
                    requests.post("http://127.0.0.1:5000/db",data={"data":post_data})
                except:
                    print("Flask server is not running.")

def get_config() -> dict:
    with open('config.json','r') as fo:
        config = json.loads(fo.read())
    return config
