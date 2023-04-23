from socket import *
from observerhelper import get_config
import json
import base64

config = get_config('config.json')
ip = config["bot_ip"]
port = config["bot_socket_port"]

class Replier:
    def __init__(self, request_data):
        self.json = request_data["json"]
        self.room = request_data["room"]

    def send_socket(self, is_success,type,data,room,msg_json):
        clientSocket = socket(AF_INET, SOCK_STREAM)
        clientSocket.connect((ip,port))

        res = { "isSuccess":is_success,
            "type":type,
            "data":base64.b64encode(data.encode()).decode(),
            "room":base64.b64encode(room.encode()).decode(),
            "msgJson":base64.b64encode(json.dumps(msg_json).encode()).decode()
            }
        print(res)
        clientSocket.send(json.dumps(res).encode("utf-8"))
        clientSocket.close()

    def reply(self,msg,room=""):
        if room == "":
            room = self.room
        self.send_socket(True,"normal",msg,self.room,self.json)