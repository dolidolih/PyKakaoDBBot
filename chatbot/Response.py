def response(room, msg, sender, replier, msg_json, db):
    if msg == "!hi":
        replier.reply("hello")
