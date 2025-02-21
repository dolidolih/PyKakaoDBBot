import sqlite3
db = sqlite3.connect('../data/data/com.kakao.talk/databases/KakaoTalk.db')
cur = db.cursor()
cur.execute('SELECT user_id FROM chat_logs WHERE v LIKE \'%\"isMine\":true%\' LIMIT 1;')
print(cur.fetchall()[0][0])
