import json
from kakaodecrypt import KakaoDecrypt
import requests

class ObserverHelper:
    def __init__(self,config):
        self.last_log_id = 0
        self.BOT_NAME = config["bot_name"]
        self.BOT_ID = config["bot_id"]
        self.COMMAND_FILE = config["command_file"]

    def is_command(self, msg):
        with open(self.COMMAND_FILE,'r') as fo:
            commands = json.loads(fo.read())
        if msg.split(' ')[0] in commands:
            return True
        else:
            return False

    def get_user_info(self, db,chat_id,user_id):
        db.cur.execute(f'SELECT name FROM db2.open_link WHERE id = (SELECT link_id FROM chat_rooms WHERE id = ?);',[chat_id])
        room = db.cur.fetchall()[0][0]
        if user_id == self.BOT_ID:
            sender = self.BOT_NAME
        else:
            sender = db.get_name_of_user_id(user_id)
        return (room, sender)

    def make_post_data(self, row, dec_msg, room, sender, js):
        data = {"msg" : dec_msg,
                "room" : room,
                "sender" : sender,
                "log_id" : row[1],
                "chat_id" : row[3],
                "hash" : row[4],
                "user_id" : row[4],
                "json" : js
                }
        return json.dumps(data)
    
    def check_change(self, db):
        print("changed")
        if self.last_log_id == 0:
            limit = 1
        else:
            limit = 5
        db.cur.execute(f'select * from chat_logs order by id desc limit ?',[limit])
        description = [desc[0] for desc in db.cur.description]
        res = db.cur.fetchall()
        res.reverse()
        for row in res:
            if row[0] > self.last_log_id:
                self.last_log_id = row[0]
                v = json.loads(row[13])
                enc = v["enc"]
                origin = v["origin"]
                enc_msg = row[5]
                user_id = row[4]
                dec_msg = KakaoDecrypt.decrypt(enc,enc_msg,user_id)
                if self.is_command(dec_msg):
                    chat_id = row[3]
                    user_info = self.get_user_info(db,chat_id,user_id)
                    room = user_info[0]
                    sender = user_info[1]
                    if room == self.BOT_NAME:
                        room = sender
                    post_data = self.make_post_data(row, dec_msg, room, sender, {description[i]:row[i] for i in range(len(row))})
                    requests.post("http://127.0.0.1:5000/db",data={"data":post_data})
                    print('sent')

def get_config(file):
    with open(file,'r') as fo:
        config = json.loads(fo.read())
    return config