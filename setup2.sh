#!/bin/bash

# 1. Guess User ID
echo "Guessing user_id of your bot."
sudo chmod -R -c 777 ~/data/data/.
BOT_ID_OUTPUT=$(python3 guess_user_id.py ~/data/data/com.kakao.talk/databases/KakaoTalk.db)
BOT_ID=$(echo "$BOT_ID_OUTPUT" | grep -oP '^\s*\K\d+' | head -n 1)

if [ -z "$BOT_ID" ]; then
    echo "Error: Could not automatically guess BOT_ID. Please check guess_user_id.py output and set BOT_ID manually."
    exit 1
fi

echo "Your bot's id seems $BOT_ID."

# 2. Set Bot Config
echo "Setting bot config..."
CURRENT_USERNAME=$(whoami)
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

# 3. Install packages
echo "Installing python3-venv, adb package."
sudo apt-get update
sudo apt-get install python3-venv adb -y

# 4. Install requirements
echo "Installing requirements."
python3 -m venv venv
venv/bin/python -m pip install pip --upgrade
venv/bin/python -m pip install -r requirements.txt

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
