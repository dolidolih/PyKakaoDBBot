from socket import *
from helper.ObserverHelper import get_config
import json
import base64

class Replier:
    def __init__(self, request_data):
        self.config = get_config()
        self.ip = self.config["bot_ip"]
        self.port = self.config["bot_socket_port"]
        self.json = request_data["json"]
        self.room = request_data["room"]

    def send_socket(self, is_success,type,data,room,msg_json):
        clientSocket = socket(AF_INET, SOCK_STREAM)
        clientSocket.connect((self.ip,self.port))

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