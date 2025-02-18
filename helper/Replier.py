from socket import *
from helper.ObserverHelper import get_config
import json
import base64
import time
import threading
from PIL import Image
import subprocess
import io

class Replier:
    def __init__(self, request_data):
        self.config = get_config()
        self.ip = self.config["bot_ip"]
        self.port = self.config["bot_socket_port"]
        self.json = request_data["json"]
        self.room = str(self.json["chat_id"])
        self.queue = []
        self.last_sent_time = time.time()

    def send_socket(self, is_success, type, data, room, msg_json):
        clientSocket = socket(AF_INET, SOCK_STREAM)
        clientSocket.connect((self.ip,self.port))

        res = { "isSuccess":is_success,
            "type":type,
            "data":base64.b64encode(data.encode()).decode(),
            "room":base64.b64encode(room.encode()).decode(),
            "msgJson":base64.b64encode(json.dumps(msg_json).encode()).decode()
            }
        clientSocket.send(json.dumps(res).encode("utf-8"))
        clientSocket.close()

    def reply(self, msg, room=None):
        if room == None:
            room = self.room
        self.__queue_message(True,"normal",str(msg),room,self.json)


    def reply_image_from_file(self, room, filepath):
        #install adb to send image
        img = Image.open(filepath)
        self.reply_image_from_image(room,img)

    def reply_image_from_image(self, room, img):
        buffered = io.BytesIO()
        img.save(buffered, format="PNG")
        png_bytes = buffered.getvalue()
        base64_bytes = base64.b64encode(png_bytes)
        base64_string = base64_bytes.decode('utf-8')
        if room == None:
            room = self.room
        self.__queue_message(True,"image",base64_string,str(room),{})
    
    def __queue_message(self, is_success, type, data, room, msg_json):
        self.queue.append((is_success, type, data, room, msg_json))
        if len(self.queue) == 1:
            self.__send_message()
    
    def __send_message(self):
        next_message = self.queue[0]
        current_time = time.time()
        if current_time-self.last_sent_time >= 0.1:
            self.send_socket(next_message[0],next_message[1],next_message[2],next_message[3],next_message[4])
            self.queue.pop(0)
            self.last_sent_time = current_time
        if len(self.queue) > 0:
            timer = threading.Timer(0.1, self.__send_message)
            timer.start()
