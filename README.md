# PyKakaoDBBot
### DEPRECATED : use Iris ( https://iris.qwer.party )

Redroid 및 노티 기반 봇앱을 이용한 파이썬 DB 봇

### 흐름도
```mermaid
sequenceDiagram
    box kakaotalk
    participant Kakaotalk
    end
    box redroid(android)
    participant SendMsg
    participant DB
    end
    box PyKakaoDBBot(linux)
    participant DBObserver
    participant Flask
    end
    DB->>DBObserver: detect changes
    DBObserver->>Flask: send commands
    Flask->>SendMsg:send result via socket
    SendMsg->>Kakaotalk:reply
```

## 1. Installation
### 1.1 Clone repository
- root가 아닌 user계정으로 진행합니다.
```shell
cd ~
git clone https://github.com/dolidolih/PyKakaoDBBot.git
cd PyKakaoDBBot
```

### 1.2 Ubuntu 24.04.1 버전 x86_64 환경 자동화(다른 OS의 경우 1.3부터 진행해주세요.)
- 쉘 스크립트 실행
```shell
chmod +x *.sh
./setup1.sh
```

- 카카오톡 설치
setup1.sh 실행 후 리드로이드가 설치되었다는 메세지가 나타났다면,
동일 네트워크의 다른 기기에서 adb, scrcpy를 통해 접속할 수 있습니다.

Redroid 내부에 카카오톡 설치 후 로그인 한 후 오픈채팅방, 일반채팅방에 메세지를 5~10개 이상 적어주세요.

- 두번째 쉘 스크립트 실행
```shell
./setup2.sh
```

실행 완료 후 두개의 서비스가 실행됩니다.
```shell
sudo systemctl status chatbot
sudo systemctl status dbobserver
```

코드 수정후에는 chatbot을 restart 해주면 새로운 코드가 적용됩니다. (sudo systemctl restart chatbot)
모두 완료되었다면 아래 단계들은 skip해도 됩니다.

### 1.3 Docker 설치
Docker의 공식 설치 가이드에 따라 설치하세요:
https://docs.docker.com/engine/install/

### 1.4 Redroid 설치 및 실행
- docker container 실행
```shell
sudo docker run -itd --privileged --name redroid\
    -v ~/data:/data \
    -p 5555:5555 \
    -p 3000:3000 \
    redroid/redroid:11.0.0-latest \
    ro.product.model=SM-T970 \
    ro.product.brand=Samsung
```
- adb, scrcpy, bot app, kakaotalk 설치
```shell
sudo apt install android-sdk-platform-tools scrcpy
adb connect localhost:5555
adb install YOUR_APP.apk
scrcpy -s localhost:5555
```

### 1.5 Config 설정
- config.json 생성하여 아래와 같이 설정합니다.
```javascript
# config.json
{
    "bot_name" : "YOUR_BOT_NAME", // 봇 이름
    "bot_id" : YOUR_BOT_ID, // 봇 ID
    "db_path" : "/home/YOUR_LINUX_USERNAME/data/data/com.kakao.talk/databases", // 리눅스 username 반영
    "bot_ip" : "127.0.0.1", // 그대로 두세요
    "bot_socket_port" : 3000 // 그대로 두세요
}
```
※ BOT_ID(봇 계정의 user_id)는 아래 스크립트를 이용하여 유추할 수 있습니다. (일반적으로 가장 짧은 데이터):
https://github.com/jiru/kakaodecrypt/blob/master/guess_user_id.py

### 1.6 파이썬 Virtual env 설정 및 기본 패키지 설치
```shell
python3 -m venv venv
source venv/bin/activate
pip install pip -- upgrade
pip install -r requirements.txt
```
### 1.7 /data 퍼미션 설정
- 초기 퍼미션 설정
```shell
sudo chmod -R -c 777 ~/data/data/.
```
- cron job 설정(정기적 퍼미션 변경)
```shell
sudo crontab -e

* * * * * /bin/chmod -R -c 777 /home/YOUR_USER_NAME/data/data/.
```
### 1.8 SendMsg 설치
- adb를 이용하여 SendMsg.dex를 안드로이드로 옮깁니다.
```shell
adb push SendMsg.dex /data/local/tmp/.
```
----
## 2. 사용 방법
### 2.1 Python script 실행
```shell
venv/bin/python observer.py &
venv/bin/python venv/bin/gunicorn -b 0.0.0.0:5000 -w 9 app:app &
```

- Systemctl을 통한 서비스를 등록하고자 하는 경우, 2개의 .service를 열어 YOUR_PYKAKAODBBOT_HOME을 pykakaodbbot의 디렉토리로 바꿔줍니다.
- 이후 /etc/systemd/system/ 에 2개의 .service 파일을 복사하고,
```shell
sudo systemctl daemon-reload
sudo systemctl enable --now dbobserver
sudo systemctl enable --now chatbot
```

- 서비스 시작 종료는 sudo systemctl start/stop/restart chatbot 등으로 수행하고, 로그는 sudo journalctl -fu chatbot 등으로 확인합니다.


### 2.2 봇 스크립트 수정
- chatbot/Response.py 를 수정하여 봇 스크립트를 작성하고, replier.reply() 메소드를 통해 채팅창에 출력할 수 있습니다.
- 다른 방으로 보내는 경우, replier.send_socket(self,is_success,type,data,room,msg_json) 을 이용할 수 있습니다.


## 3. Trouble shooting
- /dev/binder가 없는 경우
```shell
https://github.com/remote-android/redroid-doc/tree/master/deploy 의 배포판 별 설치방법에 따라 binder, ashmem 을 설정합니다.
```
- sudo docker exec -it [container 이름] sh 로 파일시스템 접근은 되나 adb는 안되는 경우
```shell
docker 실행 시 가장 뒤에 androidboot.redroid_gpu_mode=guest를 추가합니다.
```
- 생성되었으나 일정 시간 후 container가 죽는 경우(실행 중인 container 확인 : sudo docker ps)
```shell
CPU 가상화가 가능한 환경인지 확인합니다.
```
### End
