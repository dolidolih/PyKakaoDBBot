#!/bin/bash

# 1. Install packages
echo "Installing python3-venv, adb, sqlite3 package."
sudo apt-get update
sudo apt-get install python3-venv python3-pip adb sqlite3 -y

# 2. Guess User ID
echo "Guessing user_id of your bot."
CURRENT_USERNAME=$(whoami)
sudo chmod -R -c 777 ~/data/data/.

echo "Trying to get BOT_ID from KakaoTalk2.db..."
SQLITE_BOT_ID_OUTPUT=$(sqlite3 ~/data/data/com.kakao.talk/databases/KakaoTalk2.db 'SELECT user_id FROM chat_logs WHERE v LIKE "%\"isMine\":true%" LIMIT 1;' 2>/dev/null)
SQLITE_BOT_ID=$(echo "$SQLITE_BOT_ID_OUTPUT" | grep -oP '^\s*\K\d+' | head -n 1)

if [ -n "$SQLITE_BOT_ID" ]; then
    BOT_ID="$SQLITE_BOT_ID"
    echo "BOT_ID found from KakaoTalk2.db: $BOT_ID"
else
    echo "Failed to get BOT_ID from KakaoTalk2.db. Falling back to guess_user_id.py..."
    BOT_ID_OUTPUT=$(python3 guess_user_id.py ~/data/data/com.kakao.talk/databases/KakaoTalk.db)
    BOT_ID=$(echo "$BOT_ID_OUTPUT" | grep -oP '^\s*\K\d+' | head -n 1)
fi

if [ -z "$BOT_ID" ]; then
    echo "Error: Could not automatically guess BOT_ID. Please check guess_user_id.py output and set BOT_ID manually."
    exit 1
fi

echo "Your bot's id seems $BOT_ID."

# 3. Set Bot Config
echo "Setting bot config..."
CONFIG_JSON=$(cat <<EOF
{
    "bot_name" : "BOT",
    "bot_id" : $BOT_ID,
    "db_path" : "/home/$CURRENT_USERNAME/data/data/com.kakao.talk/databases",
    "bot_ip" : "127.0.0.1",
    "bot_socket_port" : 3000
}
EOF
)

echo "$CONFIG_JSON" > config.json

# 4. Install requirements
echo "Installing requirements."
python3 -m venv venv
venv/bin/python -m pip install pip --upgrade
venv/bin/python -m pip install -r requirements.txt
adb devices
adb push SendMsg/SendMsg.dex /data/local/tmp/.

# 5. Crontab job
echo "Creating a crontab permission job"
CRONTAB_LINE="* * * * * /bin/chmod -R -c 777 /home/$CURRENT_USERNAME/data/data/."
(crontab -l 2>/dev/null; echo "$CRONTAB_LINE") | sudo crontab -

# 6. Start services
echo "Now starting PyKakaoDBBot..."
sudo systemctl start chatbot
sudo systemctl start dbobserver

# 7. Check service status and finish
sleep 5
CHATBOT_STATUS=$(systemctl is-active chatbot)
DBOBSERVER_STATUS=$(systemctl is-active dbobserver)

if [ "$CHATBOT_STATUS" = "active" ] && [ "$DBOBSERVER_STATUS" = "active" ]; then
    echo "PyKakaoDBBot is running!"
    echo "Modify chatbot/Response.py for your codes, and restart chatbot service (sudo systemctl restart chatbot) to apply new codes"
else
    echo "Error: PyKakaoDBBot services are not running. Please check service status using:"
    echo "sudo systemctl status chatbot"
    echo "sudo systemctl status dbobserver"
fi

exit 0
