package com.deriklima.retronap.message;

import com.deriklima.retronap.session.*;

public class ValidMessageChecker extends ConditionChecker {

  public boolean checkCondition(MessageContext session, Message message) {
    if (!message.isValid()) {
      String errorMsg = "The message format is not valid";
      if (!session.isLoggedIn()) setErrorMessage(new Message(MessageTypes.SERVER_ERROR, errorMsg));
      else setErrorMessage(new Message(MessageTypes.SERVER_GENERAL_ERROR, errorMsg));
      return false;
    }
    return true;
  }
}
