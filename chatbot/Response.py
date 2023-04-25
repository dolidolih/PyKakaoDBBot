def response(room, msg, sender, replier, msg_json, db):
    if msg == "!hi":
        replier.reply("hello")
    elif msg == "!d":
        replier.reply(db.DB_PATH)
    elif msg == "!기능":
        replier.reply("없어요")