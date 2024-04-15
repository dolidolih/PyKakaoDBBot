def response(room, msg, sender, replier, msg_json, db, g):
    if msg == "!hi":
        replier.reply("hello")
