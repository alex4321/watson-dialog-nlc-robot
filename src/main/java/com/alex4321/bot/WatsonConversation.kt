package com.alex4321.bot

import com.ibm.watson.developer_cloud.dialog.v1.DialogService
import com.ibm.watson.developer_cloud.dialog.v1.model.Conversation
import com.ibm.watson.developer_cloud.natural_language_classifier.v1.NaturalLanguageClassifier
import java.util.*

class WatsonConversation (val robot: WatsonRobot) : IConversation {
    override var id: Int = 0
    override var clientID: Int = 0

    private fun dialog() : DialogService {
        val result = DialogService()
        result.setUsernameAndPassword(robot.auth.dialogUsername, robot.auth.dialogPassword)
        return result
    }

    private fun nlc() : NaturalLanguageClassifier {
        val result = NaturalLanguageClassifier()
        result.setUsernameAndPassword(robot.auth.nlcUsername, robot.auth.nlcPassword)
        return result
    }

    private fun processProfile(profile: Map<String, String>): Map<String, String> {
        val result = HashMap<String, String>()
        for (variable in profile.keys) {
            val valueCode = profile[variable] as String
            val value = valueCode.split("<value:main>")[1].split("</value:main>")[0].trim()
            result.put(variable, value)
        }
        return result
    }

    private fun processResponse(response: List<String>, profile: Map<String, String>, text: String) : List<String> {
        val result = ArrayList<String>()
        for (item in response) {
            val slitted = item.split('[')
            val itemBuilder = StringBuilder()
            for (part in slitted) {
                if (part.length == 0) {
                    continue
                }
                if (part.indexOf(']') != -1) {
                    val splittedPart = part.split(']')
                    val cmdPart = splittedPart[0]
                    val cmd = cmdPart.split(":")
                    val commandName = cmd[0]
                    val processedArgs = ArrayList<String>()
                    for (i in 1..cmd.size-1) {
                        val arg = cmd[i]
                        if (arg.length > 0 && arg[0] == '$') {
                            val variableName = arg.substring(1, arg.length)
                            processedArgs.add(profile[variableName] as String)
                        } else {
                            processedArgs.add(arg)
                        }
                    }
                    itemBuilder.append(CommandHandler.run(commandName, processedArgs, text))
                    if (splittedPart.size == 2) {
                        itemBuilder.append(splittedPart[1])
                    }
                } else {
                    itemBuilder.append(part)
                }
            }
            result.add(itemBuilder.toString())
        }
        return result
    }

    override fun intro() : Answer {
        val conversation = dialog().createConversation(robot.dialogID).execute()
        id = conversation.id
        clientID = conversation.clientId
        val profile = processProfile(dialog().getProfile(conversation).execute())
        return Answer(processResponse(conversation.response, profile, ""), profile)
    }

    override fun answer(text: String) : Answer {
        val minConfidence = 0.6
        val classification = nlc().classify(robot.nlcID, text).execute()
        val classes = classification.classes
        val textClassNames = StringBuilder()
        for (item in classes) {
            if (item.confidence >= minConfidence) {
                textClassNames.append(' ')
                textClassNames.append(item.name)
            }
        }
        val dialogInput = textClassNames.toString() + " " + text
        val conversation = Conversation()
        conversation.id = id
        conversation.dialogId = robot.dialogID
        conversation.clientId = clientID
        val answer = dialog().converse(conversation, dialogInput).execute()
        val profile = processProfile(dialog().getProfile(conversation).execute())
        return Answer(processResponse(answer.response, profile, text), profile)
    }
}
