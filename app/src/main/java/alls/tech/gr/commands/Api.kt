package alls.tech.gr.commands

import android.widget.Toast
import alls.tech.gr.GR
import alls.tech.gr.core.Utils.sendApi

class Api(
    recipient: String, sender: String
) : CommandModule("Api", recipient, sender) {
    @Command("api", help = "sending api")
    fun api(args: List<String>) {
        //logger.log( "API: ${args.joinToString(prefix = "[", separator = "",postfix = "]")}")
        val csrfToken = "P6wWsVPvsQFVTI087XcdjxQtX6I9XpoAaZQssj28"
        if (args.isNotEmpty()) {/*
            GR.showToast(
                Toast.LENGTH_SHORT,
                "API: ${args.joinToString(prefix = "[", postfix = "]")}"
            )
            */
            return sendApi(args, 7L)
        } else {
            GR.showToast(
                Toast.LENGTH_SHORT, "Please provide valid API"
            )
        }
    }

}

